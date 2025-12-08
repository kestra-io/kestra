package io.kestra.core.scheduler.vnodes;

import io.kestra.core.lock.LockService;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.SchedulerEventQueue;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Controls scheduler coordination and manages virtual node (VNode) assignments.
 * <p>
 * This service periodically checks active scheduler instances, performs leader election
 * via a distributed lock, and rebalances VNode assignments across available schedulers
 * when the active set changes.
 * </p>
 *
 * <p>Only the elected {@link VNodeController} leader performs rebalance operations, ensuring consistency across
 * the cluster.</p>
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

    private Set<String> localSchedulerServices = Set.of();

    private volatile Disposable controllerLockDisposable;
    private Instant controllerEpoch;

    public VNodeController(LockService lockService,
                           ServiceLivenessStore serviceLivenessStore,
                           SchedulerEventQueue schedulerEventQueue,
                           SchedulerConfiguration schedulerConfiguration) {
        this.serviceLivenessStore = serviceLivenessStore;
        this.schedulerConfiguration = schedulerConfiguration;
        this.schedulerEventQueue = schedulerEventQueue;
        this.lockService = lockService;
    }

    /**
     * Checks the current active scheduler instances and performs virtual node (VNode) rebalancing.
     * <p>
     * This method should be periodically invoked.
     */
    public void checkServicesAndRebalanceVNodes() {
        // Try to lock (used to elect a single SchedulerController as leader)
        if (controllerLockDisposable == null) {
            controllerLockDisposable = lockService.tryLock("vnodes", "controller").orElse(null);
            if (controllerLockDisposable != null) {
                controllerEpoch = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                log.info("Server is elected as {} leader", VNodeController.class.getSimpleName());
            }
        }

        // If Controller Leader
        if (controllerLockDisposable != null && !controllerLockDisposable.isDisposed()) {
            List<ServiceInstance> currentActiveSchedulers = fetchActiveSchedulerServices();

            // Fetch the current active scheduler services
            Set<String> activeSchedulerServices = currentActiveSchedulers.stream()
                .map(ServiceInstance::uid)
                .collect(Collectors.toSet());

            // Check if the active scheduler list is different from our local view
            if (localSchedulerServices.isEmpty() || !localSchedulerServices.equals(activeSchedulerServices)) {
                log.info("Starting VNodes rebalancing for schedulers: {}", activeSchedulerServices);
                Disposable disposable = null;
                try {
                    final CountDownLatch countDownLatch = new CountDownLatch(activeSchedulerServices.size());
                    final AtomicBoolean isControllerRejected = new AtomicBoolean(false);
                    final Set<String> replySchedulers = new HashSet<>();
                    disposable = schedulerEventQueue.subscribe(event -> {
                        if (event instanceof SchedulerEvent.VNodesAssignmentReply reply) {
                            Instant eventControllerEpoch = reply.controllerEpoch().truncatedTo(ChronoUnit.MILLIS);
                            if (reply.controllerId().equals(ServerInstance.INSTANCE_ID) && eventControllerEpoch.equals(controllerEpoch)) {
                                replySchedulers.add(reply.schedulerId());
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
                    // [1] Broadcast an initial 'SchedulerListRequest' event to all supposed active schedulers
                    schedulerEventQueue.send(new SchedulerEvent.VNodesAssignmentRequest(
                        Instant.now(),
                        ServerInstance.INSTANCE_ID,
                        controllerEpoch,
                        activeSchedulerServices
                    ));

                    try {
                        // [2] Wait for 'SchedulerListReply' from schedulers
                        Duration rebalanceTimeout = schedulerConfiguration.vnodesRebalanceTimeout();
                        boolean timeout = !countDownLatch.await(rebalanceTimeout.toMillis(), TimeUnit.MILLISECONDS);

                        // Check if the controller has been rejected
                        if (isControllerRejected.get()) {
                            log.error("VNode rebalancing was rejected by scheduler(s). This may happen if another server was elected as {} leader.", VNodeController.class.getSimpleName());
                            releaseLock();
                            return; // return immediately
                        }

                        if (timeout) {
                            Set<String> timeoutSchedulers = new HashSet<>(activeSchedulerServices);
                            timeoutSchedulers.removeAll(replySchedulers);
                            log.warn("VNode rebalancing in progress: {} scheduler(s) did not respond within the timeout period of {}. Affected schedulers: {}", timeoutSchedulers.size(), rebalanceTimeout, timeoutSchedulers);
                        }

                        // [3] Compute the VNodes assignments
                        Map<String, Set<Integer>> assignments = VNodeConsistentHashRing.of(schedulerConfiguration.vnodes())
                            .addNodes(replySchedulers)
                            .assignVNodes();

                        // [4] Broadcast a 'SchedulerListRelease' event with all VNodes assignments to all effective active schedulers
                        schedulerEventQueue.send(new SchedulerEvent.VNodesAssignmentRelease(
                            Instant.now(),
                            ServerInstance.INSTANCE_ID,
                            controllerEpoch,
                            assignments
                        ));

                        localSchedulerServices = replySchedulers;
                        log.info("Completed VNodes rebalancing for schedulers: {}", localSchedulerServices);
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

    private List<ServiceInstance> fetchActiveSchedulerServices() {
        return serviceLivenessStore.findAllInstancesInState(Service.ServiceState.RUNNING)
            .stream()
            .filter(service -> service.is(ServiceType.SCHEDULER))
            .toList();
    }

    @Override
    @PreDestroy
    public void close() {
        releaseLock();
    }

    private void releaseLock() {
        if (controllerLockDisposable != null) {
            controllerLockDisposable.dispose();
            controllerLockDisposable = null;
        }
    }
}
