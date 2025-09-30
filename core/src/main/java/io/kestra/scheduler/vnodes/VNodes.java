package io.kestra.scheduler.vnodes;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utility class for computing vNodes.
 */
public final class VNodes {
    
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_32_fixed();
    
    /**
     * Computes the consistent hash for the given key.
     * 
     * @param key   the key.
     * @return a hash.
     */
    public static int hash(final String key) {
        int hash = HASH_FUNCTION.hashString(key, StandardCharsets.UTF_8).asInt();
        return hash & 0x7fffffff; // ensure positive
    }
    
    /**
     * Computes the vNode owning the given trigger.
     *
     * @param id         the trigger id.
     * @param vNodeCount the total number of vNodes.
     * @return a vNode id.
     */
    public static int computeVNodeFromTrigger(final TriggerId id, int vNodeCount) {
        Objects.requireNonNull(id, "id cannot be null");
        return computeVNode(vNodeCount, FlowId.uidWithoutRevision(FlowId.of(id.getTenantId(), id.getNamespace(), id.getFlowId(), null)));
    }
    
    /**
     * Computes the vNode owning the given flow.
     *
     * @param id         the flow id.
     * @param vNodeCount the total number of vNodes.
     * @return a vNode id.
     */
    public static int computeVNodeFromFlow(final FlowId id, int vNodeCount) {
        Objects.requireNonNull(id, "id cannot be null");
        return computeVNode(vNodeCount, FlowId.uidWithoutRevision(id));
    }
    
    private static int computeVNode(int vNodeCount, String key) {
        return Math.floorMod(hash(key), vNodeCount);
    }
}
