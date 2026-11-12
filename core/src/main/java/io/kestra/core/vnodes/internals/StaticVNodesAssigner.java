package io.kestra.core.vnodes.internals;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.utils.Disposable;
import io.kestra.core.vnodes.VNodeConsistentHashRing;
import io.kestra.core.vnodes.VNodesAssigner;

/**
 * Simple {@link VNodesAssigner} which immediately rebalance VNodes on subscribe.
 * <p>
 * Only for testing-purpose.
 */
public class StaticVNodesAssigner implements VNodesAssigner {
    private static final Logger LOG = LoggerFactory.getLogger(StaticVNodesAssigner.class);

    private final int vNodeCount;

    private final Map<String, VNodeAssignmentListener> listeners = new ConcurrentHashMap<>();

    public StaticVNodesAssigner(final int vNodeCount) {
        this.vNodeCount = vNodeCount;
    }

    /** {@inheritDoc} **/
    @Override
    public Disposable subscribe(String service, VNodeAssignmentListener listener) {
        // Register the new listener
        listeners.put(service, new LoggerTriggerAssignmentListener(listener, LOG, service));

        // Re-compute all VNodes assignments
        Map<String, Set<Integer>> assignments = VNodeConsistentHashRing.of(vNodeCount)
            .addNodes(listeners.keySet())
            .assignVNodes();

        // Revoke vNodes for all listeners
        listeners.forEach((s, l) -> l.onVNodesRevoked());

        // Assign vNodes for all listeners
        listeners.forEach((s, l) -> l.onVNodesAssigned(assignments.get(service)));

        return Disposable.of(() -> this.listeners.remove(service));
    }
}
