package io.kestra.core.vnodes.internals;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.utils.Disposable;
import io.kestra.core.vnodes.VNodesAssigner;
import io.kestra.core.vnodes.VNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssignmentEvent;


/**
 * Default implementation of {@link VNodesAssigner} that assigns virtual nodes (vNodes) to the active services of one
 * {@link VNodesAssignmentChannel}.
 */
public class DefaultVNodesAssigner implements VNodesAssigner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVNodesAssigner.class);

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final VNodesAssignmentChannel channel;

    private final Map<String, VNodeAssignmentSubscription> subscriptions = new ConcurrentHashMap<>();

    private volatile Disposable disposable;

    public DefaultVNodesAssigner(VNodesAssignmentChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
    }

    public void start() {
        this.disposable = channel.subscribe(
            event -> subscriptions.values().forEach(
                subscription -> handle(event, subscription)
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

    private void handle(VNodesAssignmentEvent event, VNodeAssignmentSubscription subscription) {

        final ControllerIdAndEpoch currentControllerIdAndEpoch = subscription.controllerIdAndEpoch();

        // Check whether the event must be ignored
        if (event instanceof VNodesAssignmentEvent.VNodesAssignmentRejected) {
            return;
        }

        // Check whether the received event is stale.
        // Note: 'currentControllerIdAndEpoch' is null when no vNode rebalance is in progress for the current subscription.
        if (currentControllerIdAndEpoch != null) {
            boolean sameController = event.controllerId().equals(currentControllerIdAndEpoch.controllerId());
            boolean newerEpoch = event.controllerEpoch().isAfter(currentControllerIdAndEpoch.controllerEpoch());
            /*
             * Stale if one of these conditions is true :
             * - Different controller, epoch ≤ current
             * - Same controller, epoch < current
             */
            boolean stale = (!sameController && !newerEpoch) || (sameController && event.controllerEpoch().isBefore(currentControllerIdAndEpoch.controllerEpoch()));

            if (stale) {
                LOG.warn(
                    "Received '{}' event from out-dated controller [id={}, epoch={}]. Ignored",
                    VNodesAssignmentEvent.VNodesAssignmentRelease.class.getSimpleName(),
                    event.controllerId(),
                    event.controllerEpoch()
                );

                channel.send(
                    new VNodesAssignmentEvent.VNodesAssignmentRejected(
                        Instant.now(),
                        channel.serviceType(),
                        event.controllerId(),
                        event.controllerEpoch()
                    )
                );
                return;
            }
        }

        switch (event) {
            case VNodesAssignmentEvent.VNodesAssignmentRequest request -> {
                onAssignmentRequest(subscription, request);
            }

            case VNodesAssignmentEvent.VNodesAssignmentRelease release -> {
                onAssignmentRelease(subscription, release, currentControllerIdAndEpoch);
            }

            default -> {
                // unreachable because we already filtered other kinds
            }
        }
    }

    private void onAssignmentRequest(VNodeAssignmentSubscription subscription, VNodesAssignmentEvent.VNodesAssignmentRequest request) {
        subscription.listener().onVNodesRevoked();

        if (!request.services().contains(subscription.serviceId())) {
            LOG.warn(
                "Received '{}' event from controller {} for a rebalance this {} service is not part of. Ignored",
                VNodesAssignmentEvent.VNodesAssignmentRequest.class.getSimpleName(),
                request.controllerId(),
                channel.serviceType()
            );
            return;
        }

        subscriptions.put(
            subscription.serviceId(),
            subscription.controllerIdAndEpoch(
                new ControllerIdAndEpoch(
                    request.controllerId(), request.controllerEpoch()
                )
            )
        );

        channel.send(
            new VNodesAssignmentEvent.VNodesAssignmentReply(
                Instant.now(),
                channel.serviceType(),
                request.controllerId(),
                request.controllerEpoch(),
                subscription.serviceId()
            )
        );
    }

    private void onAssignmentRelease(VNodeAssignmentSubscription subscription, VNodesAssignmentEvent.VNodesAssignmentRelease release, ControllerIdAndEpoch currentController) {
        ControllerIdAndEpoch incoming = new ControllerIdAndEpoch(release.controllerId(), release.controllerEpoch());

        if (currentController == null) {
            LOG.warn(
                "Received '{}' event from unknown controller {}. Ignored",
                VNodesAssignmentEvent.VNodesAssignmentRelease.class.getSimpleName(), incoming
            );
            return;
        }

        if (!currentController.equals(incoming)) {
            LOG.warn(
                "Received '{}' event from invalid controller. Expected {}, but was {}.",
                VNodesAssignmentEvent.VNodesAssignmentRelease.class.getSimpleName(), currentController, incoming
            );

            if (incoming.controllerEpoch().isAfter(currentController.controllerEpoch())) {
                subscriptions.put(
                    subscription.serviceId(),
                    subscription.controllerIdAndEpoch(null)
                );
            }
            return;
        }

        final Set<Integer> vNodes = Optional.ofNullable(release.assignments())
            .map(m -> m.get(subscription.serviceId()))
            .orElse(Set.of());

        if (!vNodes.isEmpty()) {
            subscription.listener().onVNodesAssigned(vNodes);
        } else {
            LOG.warn(
                "Received '{}' from controller {} with no vNode assignment.",
                VNodesAssignmentEvent.VNodesAssignmentRelease.class.getSimpleName(), currentController
            );
        }

        subscriptions.put(subscription.serviceId(), subscription.controllerIdAndEpoch(null));
    }

    /**
     * Stops.
     * <p>
     * This method is idempotent and safe to call multiple times.
     */
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
     * @param serviceId the service identifier.
     * @param listener the vNode rebalance listener.
     */
    private record VNodeAssignmentSubscription(
        ControllerIdAndEpoch controllerIdAndEpoch,
        String serviceId,
        VNodeAssignmentListener listener) {
        public VNodeAssignmentSubscription controllerIdAndEpoch(ControllerIdAndEpoch controllerIdAndEpoch) {
            return new VNodeAssignmentSubscription(controllerIdAndEpoch, serviceId, listener);
        }
    }

    /**
     * Represents a VNode controller generation.
     *
     * @param controllerId the controller identifier.
     * @param controllerEpoch the controller epoch.
     */
    private record ControllerIdAndEpoch(String controllerId, Instant controllerEpoch) {

        @Override
        public String toString() {
            return "[controllerId=" + controllerId + ", controllerEpoch=" + controllerEpoch + "]";
        }
    }
}
