package io.kestra.core.scheduler.vnodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Implements a consistent hashing ring for virtual nodes (VNodes) to distribute load
 * across a set of physical nodes.
 * <p>
 * Each physical node can have multiple tokens on the ring, allowing for finer-grained
 * distribution. VNodes are deterministically assigned to nodes based on hash values.
 */
public class VNodeConsistentHashRing {
    
    public static final int DEFAULT_TOKENS_PER_NODE = 8;
    
    private final int vNodeCount;
    private final int tokensPerNode;
    
    private final SortedMap<Integer, String> ring = new TreeMap<>();
    
    /**
     * Static helper method for constructing a new {@link VNodeConsistentHashRing}.
     *
     * @param vNodeCount the number of vNode count.
     * @return a new {@link VNodeConsistentHashRing}.
     */
    public static VNodeConsistentHashRing of(int vNodeCount) {
        return new VNodeConsistentHashRing(vNodeCount, DEFAULT_TOKENS_PER_NODE);
    }
    
    /**
     * Creates a new {@link VNodeConsistentHashRing} instance.
     *
     * @param vNodeCount    the number of vNode count.
     * @param tokensPerNode the number of token per node.
     */
    public VNodeConsistentHashRing(int vNodeCount, int tokensPerNode) {
        this.vNodeCount = vNodeCount;
        this.tokensPerNode = tokensPerNode;
    }
    
    /**
     * Adds multiple nodes to the ring.
     *
     * @param nodeIds the list of node identifiers.
     * @return {@code this}.
     */
    public VNodeConsistentHashRing addNodes(final Set<String> nodeIds) {
        nodeIds.forEach(this::addNode);
        return this;
    }
    
    /**
     * Adds a node to the ring.
     *
     * @param nodeId the node identifiers.
     * @return {@code this}.
     */
    public VNodeConsistentHashRing addNode(final String nodeId) {
        for (int i = 0; i < tokensPerNode; i++) {
            int hash = VNodes.hash(nodeId + "#" + i);
            ring.put(hash, nodeId);
        }
        return this;
    }
    
    /**
     * Removes a node from the ring.
     *
     * @param nodeId the node identifiers.
     */
    public void removeNode(final String nodeId) {
        ring.entrySet().removeIf(e -> e.getValue().equals(nodeId));
    }
    
    /**
     * Computes the VNodes assignments for each service node added to the ring.
     *
     * @return the assignment map.
     */
    public Map<String, Set<Integer>> assignVNodes() {
        if (ring.isEmpty()) {
            return Map.of(); // fast path
        }
        
        Map<String, Set<Integer>> assignment = new HashMap<>();
        for (int vnode = 0; vnode < vNodeCount; vnode++) {
            SortedMap<Integer, String> tail = ring.tailMap(VNodes.hash("vNode-" + vnode));
            int key = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
            String nodeId = ring.get(key);
            assignment.computeIfAbsent(nodeId, k -> new HashSet<>()).add(vnode);
        }
        return assignment;
    }
}
