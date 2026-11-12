package io.kestra.core.scheduler.vnodes;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.lock.LockService;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.queue.SchedulerEventQueue;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Controls the coordination of the services sharing vnodes, and manages their virtual node (VNode) assignments.
 * <p>
 * The schedulers always share the vnodes; another service type shares them too once a {@link VNodesGroup} bean declares it.
 * Each service type is rebalanced on its own: this service periodically checks its active instances, performs leader
 * election via a distributed lock, and rebalances VNode assignments across them when the active set changes.
 * </p>
 *
 * <p>
 * Only the elected {@link VNodeController} leader of a service type performs its rebalance operations, ensuring
 * consistency across the cluster.
 * </p>
 * <p>
 * This class is not thread-safe.
 */
@Singleton
public class VNodeController implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VNodeController.class);

    private final LockService lockService;
    private final ServiceLivenessStore serviceLivenessStore;
    private final SchedulerEventQueue schedulerEventQueue;
    private final SchedulerConfiguration schedulerConfiguration;

    private final List<Group> groups;

    public VNodeController(LockService lockService,
        ServiceLivenessStore serviceLivenessStore,
        SchedulerEventQueue schedulerEventQueue,
        SchedulerConfiguration schedulerConfiguration) {
        this(lockService, serviceLivenessStore, schedulerEventQueue, schedulerConfiguration, List.of());
    }

    @Inject
    public VNodeController(LockService lockService,
        ServiceLivenessStore serviceLivenessStore,
        SchedulerEventQueue schedulerEventQueue,
        SchedulerConfiguration schedulerConfiguration,
        List<VNodesGroup> vNodesGroups) {
        this.serviceLivenessStore = serviceLivenessStore;
        this.schedulerConfiguration = schedulerConfiguration;
        this.schedulerEventQueue = schedulerEventQueue;
        this.lockService = lockService;
        this.groups = Stream.concat(Stream.of(ServiceType.SCHEDULER), vNodesGroups.stream().map(VNodesGroup::serviceType))
            .distinct()
            .map(Group::new)
            .toList();
    }

    /**
     * Checks the current active instances of each service type sharing vnodes and performs virtual node (VNode) rebalancing.
     * <p>
     * This method should be periodically invoked.
     */
    public void checkServicesAndRebalanceVNodes() {
        groups.forEach(Group::checkServicesAndRebalanceVNodes);
    }

    @Override
    @PreDestroy
    public void close() {
        groups.forEach(Group::releaseLock);
    }

    /**
     * The rebalance state of one service type.
     */
    private final class Group {
        private final ServiceType serviceType;
        private final String lockId;

        private Set<String> localServices = Set.of();

        private volatile Disposable controllerLockDisposable;
        private Instant controllerEpoch;

        private Group(ServiceType serviceType) {
            this.serviceType = serviceType;
            // The schedulers keep the lock id older servers elect their leader with.
            this.lockId = serviceType == ServiceType.SCHEDULER ? "controller" : "controller-" + serviceType.name().toLowerCase();
        }

        private void checkServicesAndRebalanceVNodes() {
            // Try to lock (used to elect a single controller as leader of this service type)
            if (controllerLockDisposable == null) {
                controllerLockDisposable = lockService.tryLock("vnodes", lockId).orElse(null);
                if (controllerLockDisposable != null) {
                    controllerEpoch = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                    log.info("Server is elected as {} leader for {}", VNodeController.class.getSimpleName(), serviceType);
                }
            }

            // If Controller Leader
            if (controllerLockDisposable != null && !controllerLockDisposable.isDisposed()) {
                List<ServiceInstance> currentActiveServices = fetchActiveServices();

                // Fetch the current active services
                Set<String> activeServices = currentActiveServices.stream()
                    .map(ServiceInstance::uid)
                    .collect(Collectors.toSet());

                // Check if the active service list is different from our local view
                if (!localServices.equals(activeServices)) {
                    log.info("Starting VNodes rebalancing for {}: {}", serviceType, activeServices);
                    Disposable disposable = null;
                    try {
                        final CountDownLatch countDownLatch = new CountDownLatch(activeServices.size());
                        final AtomicBoolean isControllerRejected = new AtomicBoolean(false);
                        final Set<String> replyServices = new HashSet<>();
                        disposable = schedulerEventQueue.subscribe(event ->
                        {
                            if (!(event instanceof SchedulerEvent.VNodesAssignmentEvent assignmentEvent) || assignmentEvent.resolvedServiceType() != serviceType) {
                                return;
                            }

                            if (event instanceof SchedulerEvent.VNodesAssignmentReply reply) {
                                Instant eventControllerEpoch = reply.controllerEpoch().truncatedTo(ChronoUnit.MILLIS);
                                if (reply.controllerId().equals(ServerInstance.INSTANCE_ID) && eventControllerEpoch.equals(controllerEpoch)) {
                                    replyServices.add(reply.schedulerId());
                                    countDownLatch.countDown();
                                }
                            }

                            if (event instanceof SchedulerEvent.VNodesAssignmentRejected rejected) {
                                Instant eventControllerEpoch = rejected.controllerEpoch().truncatedTo(ChronoUnit.MILLIS);
                                if (rejected.controllerId().equals(ServerInstance.INSTANCE_ID) && eventControllerEpoch.equals(controllerEpoch)) {
                                    isControllerRejected.set(true);
                                    // Force the latch to zero to wake up the thread
                                    while (countDownLatch.getCount() > 0) {
                                        countDownLatch.countDown();
                                    }
                                }
                            }
                        });
                        // [1] Broadcast an initial 'VNodesAssignmentRequest' event to all supposed active services
                        schedulerEventQueue.send(
                            new SchedulerEvent.VNodesAssignmentRequest(
                                Instant.now(),
                                ServerInstance.INSTANCE_ID,
                                controllerEpoch,
                                activeServices,
                                eventServiceType()
                            )
                        );

                        try {
                            // [2] Wait for 'VNodesAssignmentReply' from services
                            Duration rebalanceTimeout = schedulerConfiguration.vnodesRebalanceTimeout();
                            boolean timeout = !countDownLatch.await(rebalanceTimeout.toMillis(), TimeUnit.MILLISECONDS);

                            // Check if the controller has been rejected
                            if (isControllerRejected.get()) {
                                log.error(
                                    "VNode rebalancing was rejected by {}(s). This may happen if another server was elected as {} leader.", serviceType,
                                    VNodeController.class.getSimpleName()
                                );
                                releaseLock();
                                return; // return immediately
                            }

                            if (timeout) {
                                Set<String> timeoutServices = new HashSet<>(activeServices);
                                timeoutServices.removeAll(replyServices);
                                log.warn(
                                    "VNode rebalancing in progress: {} {}(s) did not respond within the timeout period of {}. Affected services: {}", timeoutServices.size(),
                                    serviceType, rebalanceTimeout, timeoutServices
                                );
                            }

                            // [3] Compute the VNodes assignments
                            Map<String, Set<Integer>> assignments = VNodeConsistentHashRing.of(schedulerConfiguration.vnodes())
                                .addNodes(replyServices)
                                .assignVNodes();

                            // [4] Broadcast a 'VNodesAssignmentRelease' event with all VNodes assignments to all effective active services
                            schedulerEventQueue.send(
                                new SchedulerEvent.VNodesAssignmentRelease(
                                    Instant.now(),
                                    ServerInstance.INSTANCE_ID,
                                    controllerEpoch,
                                    assignments,
                                    eventServiceType()
                                )
                            );

                            localServices = replyServices;
                            log.info("Completed VNodes rebalancing for {}: {}", serviceType, localServices);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } finally {
                        if (disposable != null) {
                            disposable.dispose();
                        }
                    }
                }
            }
        }

        /**
         * @return the service type carried by the events, null for the schedulers so that their events stay readable by older servers
         */
        private ServiceType eventServiceType() {
            return serviceType == ServiceType.SCHEDULER ? null : serviceType;
        }

        private List<ServiceInstance> fetchActiveServices() {
            return serviceLivenessStore.findAllInstancesInStates(Set.of(Service.ServiceState.RUNNING, Service.ServiceState.MAINTENANCE))
                .stream()
                .filter(service -> service.is(serviceType))
                .toList();
        }

        private void releaseLock() {
            if (controllerLockDisposable != null) {
                controllerLockDisposable.dispose();
                controllerLockDisposable = null;
            }
        }
    }

    /**
     * Declares a service type, other than the scheduler, whose instances share the vnodes and subscribe to their assignment
     * through {@link VNodesAssigner#subscribe(ServiceType, String, VNodesAssigner.VNodeAssignmentListener)}.
     *
     * @param serviceType the service type.
     */
    public record VNodesGroup(ServiceType serviceType) {
        public VNodesGroup {
            Objects.requireNonNull(serviceType, "serviceType must not be null");
        }
    }
}
