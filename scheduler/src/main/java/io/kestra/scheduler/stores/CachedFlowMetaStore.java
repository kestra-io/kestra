package io.kestra.scheduler.stores;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.vnodes.VNodes;

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
            state.ifPresent(
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
        long start = System.currentTimeMillis();
        LOG.info("Loading flows for vNodes {}", vNodes);
        // Create or warm up caches for new vNodes
        AtomicInteger count = new AtomicInteger(0);
        for (Integer vnode : vNodes) {
            partitionedCache.computeIfAbsent(vnode, key ->
            {

                Cache<String, FlowWithSource> cache = newCache();

                List<FlowWithSource> states = delegate.findAllForVNodes(Set.of(vnode));
                states.forEach(state -> cache.put(FlowId.uidWithoutRevision(state), state));
                count.addAndGet(states.size());
                return cache;
            });
        }
        LOG.info("{} flows loaded for vNodes {} in {}ms", count, vNodes, System.currentTimeMillis() - start);
    }

    public void clear() {
        LOG.info("Clearing local cache");
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
