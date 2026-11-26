package io.kestra.scheduler.stores;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.vnodes.VNodes;

import jakarta.inject.Inject;

public class CachedFlowMetaStore implements FlowMetaStore {

    private static final Logger LOG = LoggerFactory.getLogger(CachedFlowMetaStore.class);

    private final FlowMetaStore delegate;
    private final SchedulerConfiguration schedulerConfiguration;
    private final Map<Integer, Cache<String, FlowWithSource>> partitionedCache = new ConcurrentHashMap<>();

    @Inject
    public CachedFlowMetaStore(FlowMetaStore delegate, SchedulerConfiguration schedulerConfiguration) {
        this.delegate = delegate;
        this.schedulerConfiguration = schedulerConfiguration;
    }

    private Cache<String, FlowWithSource> newCache() {
        return Caffeine.newBuilder()
            .maximumSize(schedulerConfiguration.cacheMaxSizePerVNode())
            .build();
    }

    // -------------------------------------------------------------------------
    // Delegate passthrough methods
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<FlowWithSource> find(FlowId flowId) {
        final int vNode = VNodes.computeVNodeFromFlow(flowId, schedulerConfiguration.vnodes());
        final String cacheKey = FlowId.uidWithoutRevision(flowId);

        FlowWithSource cached = Optional.ofNullable(partitionedCache.get(vNode))
            .map(cache -> cache.getIfPresent(cacheKey))
            .orElse(null);

        // If cache missed
        if (cached == null) {
            Optional<FlowWithSource> state = delegate.find(flowId);
            // Caching an older revision would make it the one every lookup without a revision returns.
            Optional<FlowWithSource> latest = flowId.getRevision() == null ? state : delegate.find(FlowId.of(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), null));
            latest.ifPresent(
                s -> partitionedCache
                    .computeIfAbsent(vNode, k -> newCache())
                    .put(cacheKey, s)
            );
            return state;
        }

        // If null or same revision, then return the cached value
        if (flowId.getRevision() == null || flowId.getRevision().equals(cached.getRevision())) {
            return Optional.of(cached);
        }

        // Otherwise, fetch new revision, and update the cache if more recent
        Optional<FlowWithSource> maybeNewRevision = delegate.find(flowId);
        maybeNewRevision.ifPresent(current ->
        {
            if (current.getRevision() > cached.getRevision()) {
                partitionedCache.computeIfAbsent(vNode, k -> newCache()).put(cacheKey, current);
            } else {
                // must not happen. Otherwise, this would mean that:
                // - TriggerEvent are not consumed in order for a same flow
                // - Concurrent access between the TriggerEventHandler and TriggerScheduler (not possible by design)
                LOG.warn("Unexpected access to flow metastore for earliest revision {}, expected {}.", current.getRevision(), cached.getRevision());
            }
        });
        return maybeNewRevision;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(FlowId flowId) {
        final int vNode = VNodes.computeVNodeFromFlow(flowId, schedulerConfiguration.vnodes());
        Cache<String, FlowWithSource> cache = partitionedCache.get(vNode);
        if (cache == null) {
            return;
        }

        final String cacheKey = FlowId.uidWithoutRevision(flowId);
        delegate.find(FlowId.of(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), null))
            .filter(flow -> !flow.isDeleted())
            .ifPresentOrElse(flow -> cache.put(cacheKey, flow), () -> cache.invalidate(cacheKey));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowWithSource> findAllForVNodes(final Set<Integer> vNodes) {
        // Check if all requested vNodes are cached
        boolean allCached = vNodes.stream().allMatch(partitionedCache::containsKey);

        if (allCached && !vNodes.isEmpty()) {
            return findAllForNodesFromCache(vNodes);
        }

        // Fallback to delegate and update caches for missing vNodes);
        loadCacheForAllVNodes(vNodes);

        return findAllForNodesFromCache(vNodes);
    }

    private List<@NonNull FlowWithSource> findAllForNodesFromCache(final Set<Integer> vNodes) {
        return vNodes.stream()
            .map(partitionedCache::get)
            .filter(Objects::nonNull)
            .flatMap(cache -> cache.asMap().values().stream())
            .toList();
    }

    // -------------------------------------------------------------------------
    // Cache lifecycle management
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final Set<Integer> vNodes) {
        if (vNodes == null || vNodes.isEmpty()) {
            clear();
            return;
        }

        // Remove caches for revoked vNodes
        Set<Integer> currentVNodes = new HashSet<>(partitionedCache.keySet());
        for (Integer vnode : currentVNodes) {
            if (!vNodes.contains(vnode)) {
                LOG.debug("Revoking vNode cache {}", vnode);
                Optional.ofNullable(partitionedCache.remove(vnode))
                    .ifPresent(Cache::invalidateAll);
            }
        }

        loadCacheForAllVNodes(vNodes);
    }

    private void loadCacheForAllVNodes(final Set<Integer> vNodes) {
        Set<Integer> missing = vNodes.stream()
            .filter(vNode -> !partitionedCache.containsKey(vNode))
            .collect(Collectors.toSet());
        if (missing.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        LOG.debug("Loading flows for vNodes {}", missing);
        Map<Integer, Cache<String, FlowWithSource>> loaded = HashMap.newHashMap(missing.size());
        missing.forEach(vNode -> loaded.put(vNode, newCache()));

        // One call for all vNodes, since the repository reads every flow whatever the vNodes asked for.
        List<FlowWithSource> flows = delegate.findAllForVNodes(missing);
        flows.forEach(flow ->
        {
            Cache<String, FlowWithSource> cache = loaded.get(VNodes.computeVNodeFromFlow(flow, schedulerConfiguration.vnodes()));
            if (cache != null) {
                cache.put(FlowId.uidWithoutRevision(flow), flow);
            }
        });
        loaded.forEach(partitionedCache::putIfAbsent);
        LOG.debug("{} flows loaded for vNodes {} in {}ms", flows.size(), missing, System.currentTimeMillis() - start);
    }

    public void clear() {
        LOG.debug("Clearing local cache");
        partitionedCache.values().forEach(Cache::invalidateAll);
        partitionedCache.clear();
    }

    @VisibleForTesting
    public int cacheSize() {
        return partitionedCache.values().stream()
            .mapToInt(c -> c.asMap().size())
            .sum();
    }
}
