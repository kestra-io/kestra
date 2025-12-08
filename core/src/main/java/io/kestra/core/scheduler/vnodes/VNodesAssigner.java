package io.kestra.core.scheduler.vnodes;

import io.kestra.core.utils.Disposable;
import org.slf4j.Logger;

import java.util.Set;

/**
 A service interface responsible for managing and notifying services about virtual nodes
 * assignment changes across distributed environments.
 */
public interface VNodesAssigner {
    
    /**
     * Subscribes a {@link VNodeAssignmentListener} to receive notifications
     * about trigger vNode assignment changes for the specified scheduler.
     * <p>
     * Once subscribed, the listener will be notified whenever:
     * <ul>
     *   <li>All current trigger assignments are revoked, via
     *       {@link VNodeAssignmentListener#onVNodesRevoked()}, or</li>
     *   <li>New trigger assignments are made, via
     *       {@link VNodeAssignmentListener#onVNodesAssigned(Set)}.</li>
     * </ul>
     *
     * @param service  the unique identifier of the scheduler service subscribing
     *                 to trigger assignment updates; must not be {@code null}
     * @param listener the listener that will receive trigger assignment notifications;
     *                 must not be {@code null}
     * @throws NullPointerException if {@code service} or {@code listener} is {@code null}
     * 
     * @return a {@link Disposable} to stop listening for VNode re-assignment.
     */
    Disposable subscribe(String service, VNodeAssignmentListener listener);
    
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
        
        private Set<Integer> currentAssignment = Set.of() ;
        
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
