package io.kestra.scheduler.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.vnodes.VNodes;
import io.kestra.scheduler.stores.FlowMetaStore;

/**
 * In-memory implementation of FlowMetaStore.
 * <p>
 * This class MUST only be used for testing purpose.
 */
public final class InMemoryFlowMetaStore implements FlowMetaStore {

    private final Map<Integer, Map<String, NavigableMap<Integer, FlowWithSource>>> flowStoresByVNode = new ConcurrentHashMap<>();

    private final int vnodes;

    public InMemoryFlowMetaStore(int vnodes, List<FlowWithSource> flows) {
        this.vnodes = vnodes;
        flows.forEach(this::add);
    }

    /**
     * Adds a revision of a flow to the store, replacing the one with the same revision if any.
     *
     * @param flow the flow to add
     */
    public void add(FlowWithSource flow) {
        int vNode = VNodes.computeVNodeFromFlow(flow, vnodes);

        flowStoresByVNode.computeIfAbsent(vNode, (key) -> new ConcurrentHashMap<>())
            .computeIfAbsent(flow.uidWithoutRevision(), (key) -> new ConcurrentSkipListMap<>())
            .put(flow.getRevision(), flow);
    }

    /**
     * Finds the requested revision of a flow, or its latest revision when that one is unknown.
     */
    @Override
    public Optional<FlowWithSource> find(FlowId flowId) {
        int vNode = VNodes.computeVNodeFromFlow(flowId, vnodes);
        return Optional.ofNullable(flowStoresByVNode.get(vNode))
            .map(store -> store.get(FlowId.uidWithoutRevision(flowId)))
            .map(revisions -> Optional.ofNullable(flowId.getRevision()).map(revisions::get).orElseGet(() -> revisions.lastEntry().getValue()));
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
            Map<String, NavigableMap<Integer, FlowWithSource>> store = flowStoresByVNode.get(vNode);
            if (store != null) {
                store.values().forEach(revisions -> result.add(revisions.lastEntry().getValue()));
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
