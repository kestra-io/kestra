package io.kestra.scheduler.utils;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.scheduler.stores.FlowMetaStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of FlowMetaStore.
 * <p>
 * This class MUST only be used for testing purpose.
 */
public final class InMemoryFlowMetaStore implements FlowMetaStore {
    
    // Map from FlowId to FlowWithSource
    private final Map<Integer, Map<String, FlowWithSource>> flowStoresByVNode = new ConcurrentHashMap<>();
    
    private final int vnodes;
    
    public InMemoryFlowMetaStore(int vnodes, List<FlowWithSource> flows) {
        this.vnodes = vnodes;
        flows.forEach(this::add);
    }
    
    /**
     * Adds or replaces a FlowWithSource in the store.
     *
     * @param flow the flow to add
     */
    public void add(FlowWithSource flow) {
        int vNode = VNodes.computeVNodeFromFlow(flow, vnodes);
        
        Map<String, FlowWithSource> store = flowStoresByVNode.computeIfAbsent(vNode, (key) -> new ConcurrentHashMap<>());
        store.put(flow.uidWithoutRevision(), flow);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<FlowWithSource> find(FlowId flowId) {
        int vNode = VNodes.computeVNodeFromFlow(flowId, vnodes);
        return Optional.ofNullable(flowStoresByVNode.get(vNode)).map(store -> store.get(FlowId.uidWithoutRevision(flowId)));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowWithSource> findAllForVNodes(Set<Integer> vNodes) {
        if (vNodes == null || vNodes.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<FlowWithSource> result = new ArrayList<>();
        for (Integer vNode : vNodes) {
            Map<String, FlowWithSource> store = flowStoresByVNode.get(vNode);
            if (store != null) {
               result.addAll(store.values()); 
            }
        }
        return result;
    }
    
    /**
     * Clears all data from the in-memory store.
     */
    public void clear() {
        flowStoresByVNode.clear();
    }
}
