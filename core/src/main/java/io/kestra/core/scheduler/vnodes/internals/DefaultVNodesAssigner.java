package io.kestra.core.scheduler.vnodes.internals;

import io.kestra.core.scheduler.SchedulerEventQueue;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.SchedulerEvent.VNodesAssignmentRelease;
import io.kestra.core.scheduler.events.SchedulerEvent.VNodesAssignmentRequest;
import io.kestra.core.scheduler.vnodes.VNodesAssigner;
import io.kestra.core.utils.Disposable;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link VNodesAssigner} that assigns virtual nodes (vNodes) to active scheduler services.
 */
@Singleton
@Requires(property = "kestra.server-type")
public class DefaultVNodesAssigner implements VNodesAssigner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVNodesAssigner.class);

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final SchedulerEventQueue queue;

    private final Map<String, VNodeAssignmentSubscription> subscriptions = new ConcurrentHashMap<>();

    private volatile Disposable disposable;

    /**
     * Creates a new DefaultTriggerAssigner with the default vNode count of 16.
     */
    @Inject
    public DefaultVNodesAssigner(SchedulerEventQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    @PostConstruct
    public void start() {
        this.disposable = queue.subscribe(event ->
            subscriptions.values().forEach(subscription ->
                handle(event, subscription)
            )
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Disposable subscribe(String service, VNodeAssignmentListener listener) {
        Objects.requireNonNull(service, "serviceId must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        if (stopped.get()) {
            throw new IllegalStateException("VNodeAssigner has been stopped");
        }

        if (subscriptions.containsKey(service)) {
            throw new IllegalStateException("Service [" + service + "] has already subscribed to vNodes (re)assignment.");
        }

        this.subscriptions.put(service, new VNodeAssignmentSubscription(null, service, new LoggerTriggerAssignmentListener(listener, LOG, service)));

        return Disposable.of(() -> this.subscriptions.remove(service));
    }

    private void handle(SchedulerEvent event, VNodeAssignmentSubscription subscription) {

        final ControllerIdAndEpoch currentControllerIdAndEpoch = subscription.controllerIdAndEpoch();

        // Check whether the event must be ignored
        if (!(event instanceof SchedulerEvent.VNodesAssignmentEvent vNodesAssignmentEvent) ||
            event instanceof SchedulerEvent.VNodesAssignmentRejected) {
            return;
        }

        // Check whether the received event is stale.
        // Note: 'currentControllerIdAndEpoch' is null when no vNode rebalance is in progress for the current subscription.
        if (currentControllerIdAndEpoch != null) {
            boolean sameController = vNodesAssignmentEvent.controllerId().equals(currentControllerIdAndEpoch.controllerId());
            boolean newerEpoch = vNodesAssignmentEvent.controllerEpoch().isAfter(currentControllerIdAndEpoch.controllerEpoch());
            /*
             * Stale if one of these conditions is true :
             * - Different controller, epoch ≤ current
             * - Same controller, epoch < current
             */
            boolean stale = (!sameController && !newerEpoch) || (sameController && vNodesAssignmentEvent.controllerEpoch().isBefore(currentControllerIdAndEpoch.controllerEpoch()));

            if (stale) {
                LOG.warn("Received '{}' event from out-dated controller [id={}, epoch={}]. Ignored",
                    VNodesAssignmentRelease.class.getSimpleName(),
                    vNodesAssignmentEvent.controllerId(),
                    vNodesAssignmentEvent.controllerEpoch());

                queue.send(new SchedulerEvent.VNodesAssignmentRejected(
                    Instant.now(),
                    vNodesAssignmentEvent.controllerId(),
                    vNodesAssignmentEvent.controllerEpoch()
                ));
                return;
            }
        }

        switch (event) {
            case SchedulerEvent.VNodesAssignmentRequest request -> {
                onAssignmentRequest(subscription, request);
            }

            case SchedulerEvent.VNodesAssignmentRelease release -> {
                onAssignmentRelease(subscription, release, currentControllerIdAndEpoch);
            }

            default -> {
                // unreachable because we already filtered other kinds
            }
        }
    }

    private void onAssignmentRequest(VNodeAssignmentSubscription subscription, VNodesAssignmentRequest request) {
        subscription.listener().onVNodesRevoked();

        if (!request.schedulers().contains(subscription.serviceId())) {
            LOG.warn(
                "Received '{}' event from controller {} for a rebalance this scheduler is not part of. Ignored",
                VNodesAssignmentRequest.class.getSimpleName(),
                request.controllerId()
            );
            return;
        }

        subscriptions.put(
            subscription.serviceId(),
            subscription.controllerIdAndEpoch(new ControllerIdAndEpoch(
                request.controllerId(), request.controllerEpoch()))
        );

        queue.send(new SchedulerEvent.VNodesAssignmentReply(
            Instant.now(),
            request.controllerId(),
            request.controllerEpoch(),
            subscription.serviceId()
        ));
    }

    private void onAssignmentRelease(VNodeAssignmentSubscription subscription, VNodesAssignmentRelease release, ControllerIdAndEpoch currentController) {
        ControllerIdAndEpoch incoming = new ControllerIdAndEpoch(release.controllerId(), release.controllerEpoch());

        if (currentController == null) {
            LOG.warn("Received '{}' event from unknown controller {}. Ignored",
                VNodesAssignmentRelease.class.getSimpleName(), incoming);
            return;
        }

        if (!currentController.equals(incoming)) {
            LOG.warn("Received '{}' event from invalid controller. Expected {}, but was {}.",
                VNodesAssignmentRelease.class.getSimpleName(), currentController, incoming);

            if (incoming.controllerEpoch().isAfter(currentController.controllerEpoch())) {
                subscriptions.put(subscription.serviceId(),
                    subscription.controllerIdAndEpoch(null));
            }
            return;
        }

        final Set<Integer> vNodes = Optional.ofNullable(release.assignments())
            .map(m -> m.get(subscription.serviceId()))
            .orElse(Set.of());

        if (!vNodes.isEmpty()) {
            subscription.listener().onVNodesAssigned(vNodes);
        } else {
            LOG.warn("Received '{}' from controller {} with no vNode assignment.",
                VNodesAssignmentRelease.class.getSimpleName(), currentController);
        }

        subscriptions.put(subscription.serviceId(), subscription.controllerIdAndEpoch(null));
    }

    /**
     * Stops.
     * <p>
     * This method is idempotent and safe to call multiple times.
     */
    @PreDestroy
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return; // Already stopped
        }

        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     * Represents a subscription for VNode assignments.
     *
     * @param controllerIdAndEpoch the controller metadata.
     * @param serviceId            the service identifier.
     * @param listener             the vNode rebalance listener.
     */
    private record VNodeAssignmentSubscription(
        ControllerIdAndEpoch controllerIdAndEpoch,
        String serviceId,
        VNodeAssignmentListener listener
    ) {
        public VNodeAssignmentSubscription controllerIdAndEpoch(ControllerIdAndEpoch controllerIdAndEpoch) {
            return new VNodeAssignmentSubscription(controllerIdAndEpoch, serviceId, listener);
        }
    }

    /**
     * Represents a VNode controller generation.
     *
     * @param controllerId    the controller identifier.
     * @param controllerEpoch the controller epoch.
     */
    private record ControllerIdAndEpoch(String controllerId, Instant controllerEpoch) {

        @Override
        public String toString() {
            return "[controllerId=" + controllerId + ", controllerEpoch=" + controllerEpoch + "]";
        }
    }
}
