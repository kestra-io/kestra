package io.kestra.core.vnodes;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.lock.LockService;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.utils.Disposable;

/**
 * Controls the coordination of a group of services and manages their virtual node (VNode) assignments.
 * <p>
 * This service periodically checks the active instances of the group, performs leader election
 * via a distributed lock, and rebalances VNode assignments across them when the active set changes.
 * </p>
 *
 * <p>
 * Only the elected {@link VNodeController} leader performs rebalance operations, ensuring consistency across
 * the cluster.
 * </p>
 * <p>
 * This class is not thread-safe.
 */
public class VNodeController implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VNodeController.class);

    private final LockService lockService;
    private final ServiceLivenessStore serviceLivenessStore;
    private final VNodesAssignmentChannel channel;
    private final int vNodeCount;
    private final Duration rebalanceTimeout;

    private Set<String> localServices = Set.of();

    private volatile Disposable controllerLockDisposable;
    private Instant controllerEpoch;

    public VNodeController(LockService lockService,
        ServiceLivenessStore serviceLivenessStore,
        VNodesAssignmentChannel channel,
        int vNodeCount,
        Duration rebalanceTimeout) {
        this.lockService = lockService;
        this.serviceLivenessStore = serviceLivenessStore;
        this.channel = channel;
        this.vNodeCount = vNodeCount;
        this.rebalanceTimeout = rebalanceTimeout;
    }

    /**
     * Checks the current active instances of the group and performs virtual node (VNode) rebalancing.
     * <p>
     * This method should be periodically invoked.
     */
    public void checkServicesAndRebalanceVNodes() {
        // Try to lock (used to elect a single controller as leader)
        if (controllerLockDisposable == null) {
            controllerLockDisposable = lockService.tryLock("vnodes", channel.controllerLockId()).orElse(null);
            if (controllerLockDisposable != null) {
                controllerEpoch = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                log.info("Server is elected as {} leader for {} services", VNodeController.class.getSimpleName(), channel.serviceType());
            }
        }

        // If Controller Leader
        if (controllerLockDisposable != null && !controllerLockDisposable.isDisposed()) {
            List<ServiceInstance> currentActiveServices = fetchActiveServices();

            Set<String> activeServices = currentActiveServices.stream()
                .map(ServiceInstance::uid)
                .collect(Collectors.toSet());

            // Check if the active service list is different from our local view
            if (!localServices.equals(activeServices)) {
                log.info("Starting VNodes rebalancing for {} services: {}", channel.serviceType(), activeServices);
                Disposable disposable = null;
                try {
                    final CountDownLatch countDownLatch = new CountDownLatch(activeServices.size());
                    final AtomicBoolean isControllerRejected = new AtomicBoolean(false);
                    final Set<String> replyServices = new HashSet<>();
                    disposable = channel.subscribe(event ->
                    {
                        if (event instanceof VNodesAssignmentEvent.VNodesAssignmentReply reply) {
                            Instant eventControllerEpoch = reply.controllerEpoch().truncatedTo(ChronoUnit.MILLIS);
                            if (reply.controllerId().equals(ServerInstance.INSTANCE_ID) && eventControllerEpoch.equals(controllerEpoch)) {
                                replyServices.add(reply.serviceId());
                                countDownLatch.countDown();
                            }
                        }

                        if (event instanceof VNodesAssignmentEvent.VNodesAssignmentRejected rejected) {
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
                    // [1] Broadcast an initial 'Request' event to all supposed active services
                    channel.send(
                        new VNodesAssignmentEvent.VNodesAssignmentRequest(
                            Instant.now(),
                            channel.serviceType(),
                            ServerInstance.INSTANCE_ID,
                            controllerEpoch,
                            activeServices
                        )
                    );

                    try {
                        // [2] Wait for 'Reply' from services
                        boolean timeout = !countDownLatch.await(rebalanceTimeout.toMillis(), TimeUnit.MILLISECONDS);

                        // Check if the controller has been rejected
                        if (isControllerRejected.get()) {
                            log.error("VNode rebalancing was rejected by {} service(s). This may happen if another server was elected as {} leader.", channel.serviceType(), VNodeController.class.getSimpleName());
                            releaseLock();
                            return; // return immediately
                        }

                        if (timeout) {
                            Set<String> timeoutServices = new HashSet<>(activeServices);
                            timeoutServices.removeAll(replyServices);
                            log.warn(
                                "VNode rebalancing in progress: {} {} service(s) did not respond within the timeout period of {}. Affected services: {}", timeoutServices.size(),
                                channel.serviceType(), rebalanceTimeout, timeoutServices
                            );
                        }

                        // [3] Compute the VNodes assignments
                        Map<String, Set<Integer>> assignments = VNodeConsistentHashRing.of(vNodeCount)
                            .addNodes(replyServices)
                            .assignVNodes();

                        // [4] Broadcast a 'Release' event with all VNodes assignments to all effective active services
                        channel.send(
                            new VNodesAssignmentEvent.VNodesAssignmentRelease(
                                Instant.now(),
                                channel.serviceType(),
                                ServerInstance.INSTANCE_ID,
                                controllerEpoch,
                                assignments
                            )
                        );

                        localServices = replyServices;
                        log.info("Completed VNodes rebalancing for {} services: {}", channel.serviceType(), localServices);
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

    private List<ServiceInstance> fetchActiveServices() {
        return serviceLivenessStore.findAllInstancesInStates(Set.of(Service.ServiceState.RUNNING, Service.ServiceState.MAINTENANCE))
            .stream()
            .filter(service -> service.is(channel.serviceType()))
            .toList();
    }

    @Override
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
