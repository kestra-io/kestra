package io.kestra.scheduler.vnodes.internals;

import io.kestra.scheduler.vnodes.VNodeConsistentHashRing;
import io.kestra.scheduler.SchedulerConfiguration;
import io.kestra.scheduler.vnodes.VNodesAssigner;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple {@link VNodesAssigner} which immediately rebalance VNodes on subscribe.
 * <p>
 * Only for testing-purpose.
 */
@Singleton
@Requires(missingBeans = VNodesAssigner.class)
public class StaticVNodesAssigner implements VNodesAssigner {
    private static final Logger LOG = LoggerFactory.getLogger(StaticVNodesAssigner.class);
    
    private final SchedulerConfiguration schedulerConfiguration;
    
    private final Map<String, VNodeAssignmentListener> listeners = new ConcurrentHashMap<>();
    
    /**
     * Creates a new {@link StaticVNodesAssigner} instance.
     * @param schedulerConfiguration    The {@link SchedulerConfiguration}.
     */
    public StaticVNodesAssigner(final SchedulerConfiguration schedulerConfiguration) {
        this.schedulerConfiguration = schedulerConfiguration;
    }
    
    /** {@inheritDoc} **/
    @Override
    public void subscribe(String service, VNodeAssignmentListener listener) {
        // Register the new listener
        listeners.put(service, new LoggerTriggerAssignmentListener(listener, LOG, service));
        
        // Re-compute all VNodes assignments
        Map<String, Set<Integer>> assignments = VNodeConsistentHashRing.of(schedulerConfiguration.vnodes())
            .addNodes(listeners.keySet())
            .assignVNodes();
        
        // Revoke vNodes for all listeners
        listeners.forEach((s, l) -> l.onVNodesRevoked());
        
        // Assign vNodes for all listeners
        listeners.forEach((s, l) -> l.onVNodesAssigned(assignments.get(service)));  
    }
}
