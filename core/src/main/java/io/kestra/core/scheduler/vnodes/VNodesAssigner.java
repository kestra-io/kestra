package io.kestra.core.scheduler.vnodes;

import java.util.Set;

import org.slf4j.Logger;

import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

/**
 * A service interface responsible for managing and notifying services about virtual nodes
 * assignment changes across distributed environments.
 */
public interface VNodesAssigner {

    /**
     * Subscribes a {@link VNodeAssignmentListener} to receive notifications
     * about trigger vNode assignment changes for the specified scheduler.
     * <p>
     * Once subscribed, the listener will be notified whenever:
     * <ul>
     * <li>All current trigger assignments are revoked, via
     * {@link VNodeAssignmentListener#onVNodesRevoked()}, or</li>
     * <li>New trigger assignments are made, via
     * {@link VNodeAssignmentListener#onVNodesAssigned(Set)}.</li>
     * </ul>
     *
     * @param service the unique identifier of the scheduler service subscribing
     *        to trigger assignment updates; must not be {@code null}
     * @param listener the listener that will receive trigger assignment notifications;
     *        must not be {@code null}
     * @throws NullPointerException if {@code service} or {@code listener} is {@code null}
     * 
     * @return a {@link Disposable} to stop listening for VNode re-assignment.
     */
    default Disposable subscribe(String service, VNodeAssignmentListener listener) {
        return subscribe(ServiceType.SCHEDULER, service, listener);
    }

    /**
     * Subscribes a {@link VNodeAssignmentListener} to the vNode assignment of a service of another type than the scheduler,
     * which a {@link VNodeController.VNodesGroup} bean must declare for its vNodes to be rebalanced.
     * <p>
     * The services of each type share all the vNodes, independently of the other types.
     *
     * @param serviceType the type of the subscribing service; must not be {@code null}
     * @param service the unique identifier of the subscribing service, as reported to the liveness store; must not be {@code null}
     * @param listener the listener that will receive the assignment notifications; must not be {@code null}
     * @return a {@link Disposable} to stop listening for VNode re-assignment.
     */
    Disposable subscribe(ServiceType serviceType, String service, VNodeAssignmentListener listener);

    /**
     * Interface for listening on trigger vNodes assignment changes.
     */
    interface VNodeAssignmentListener {

        /**
         * Invokes when all trigger assignments (vNodes) are revoked.
         * <p>
         * When this method return all vNodes are considered effectively revoked, and no trigger is
         * expected to be processed.
         */
        void onVNodesRevoked();

        /**
         * Invokes when
         *
         * @param vNodes the list of vNodes assigned to the scheduler.
         */
        void onVNodesAssigned(Set<Integer> vNodes);

        /**
         * A no-op {@link VNodeAssignmentListener} implementation.
         * <p>
         * For testing purpose only.
         */
        VNodeAssignmentListener NOOP = new VNodeAssignmentListener() {
            @Override
            public void onVNodesRevoked() {
                // noop
            }

            @Override
            public void onVNodesAssigned(Set<Integer> vNodes) {
                // noop
            }
        };
    }

    class LoggerTriggerAssignmentListener implements VNodeAssignmentListener {

        private final VNodeAssignmentListener listener;
        private final Logger logger;
        private final String service;

        private Set<Integer> currentAssignment = Set.of();

        public LoggerTriggerAssignmentListener(VNodeAssignmentListener listener, Logger logger, String service) {
            this.listener = listener;
            this.logger = logger;
            this.service = service;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onVNodesRevoked() {
            logger.info("[service: {}] vNodes revoked: {}", service, currentAssignment);
            listener.onVNodesRevoked();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onVNodesAssigned(Set<Integer> vNodes) {
            logger.info("[service: {}] vNodes assigned: {}", service, vNodes);
            listener.onVNodesAssigned(vNodes);
            this.currentAssignment = vNodes;
        }
    }
}
