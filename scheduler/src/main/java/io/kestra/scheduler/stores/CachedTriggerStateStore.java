package io.kestra.scheduler.stores;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.scheduler.model.TriggerState;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedTriggerStateStore implements TriggerStateStore {
    
    private static final Logger LOG = LoggerFactory.getLogger(CachedTriggerStateStore.class);
    
    private final TriggerStateStore delegate;
    private final SchedulerConfiguration schedulerConfiguration;
    private final Map<Integer, Cache<String, TriggerState>> partitionedCache = new ConcurrentHashMap<>();
    
    @Inject
    public CachedTriggerStateStore(TriggerStateStore delegate, SchedulerConfiguration schedulerConfiguration) {
        this.delegate = delegate;
        this.schedulerConfiguration = schedulerConfiguration;
    }
    
    private Cache<String, TriggerState> newCache() {
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
    public List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) {
        return delegate.findTriggersEligibleForScheduling(now, vNodes, locked);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<TriggerState> findAllForVNodes(final Set<Integer> vNodes) {
        // Check if all requested vNodes are cached
        boolean allCached = vNodes.stream().allMatch(partitionedCache::containsKey);
        
        if (allCached && !vNodes.isEmpty()) {
            return findAllForNodesFromCache(vNodes);
        }
        
        // Fallback to delegate and update caches for missing vNodes);
        loadCacheForAllVNodes(vNodes);
        
        return findAllForNodesFromCache(vNodes);
    }
    
    private List<@NonNull TriggerState> findAllForNodesFromCache(final Set<Integer> vNodes) {
        return vNodes.stream()
            .map(partitionedCache::get)
            .filter(Objects::nonNull)
            .flatMap(cache -> cache.asMap().values().stream())
            .toList();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TriggerState> find(TriggerId triggerId) {
        int vnode = VNodes.computeVNodeFromTrigger(triggerId, schedulerConfiguration.vnodes());
        Cache<String, TriggerState> cache = partitionedCache.get(vnode);
        
        if (cache != null) {
            TriggerState cached = cache.getIfPresent(triggerId.uid());
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        
        Optional<TriggerState> state = delegate.find(triggerId);
        state.ifPresent(s -> {
            partitionedCache
                .computeIfAbsent(vnode, k -> newCache())
                .put(s.uid(), s);
        });
        return state;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void save(TriggerState triggerState) {
        delegate.save(triggerState);
        int vnode = triggerState.getVnode();
        partitionedCache
            .computeIfAbsent(vnode, k -> newCache())
            .put(triggerState.uid(), triggerState);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(TriggerId triggerId) {
        delegate.delete(triggerId);
        int vnode = VNodes.computeVNodeFromTrigger(triggerId, schedulerConfiguration.vnodes());
        Optional.ofNullable(partitionedCache.get(vnode))
            .ifPresent(cache -> cache.invalidate(triggerId.uid()));
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
                LOG.debug("Revoking vnode cache {}", vnode);
                Optional.ofNullable(partitionedCache.remove(vnode))
                    .ifPresent(Cache::invalidateAll);
            }
        }
    }
    
    private void loadCacheForAllVNodes(final Set<Integer> vNodes) {
        long start = System.currentTimeMillis();
        LOG.info("Loading trigger states for vnodes {}", vNodes);
        // Create or warm up caches for new vNodes
        AtomicInteger count = new AtomicInteger(0);
        for (Integer vnode : vNodes) {
            partitionedCache.computeIfAbsent(vnode, key -> {

                Cache<String, TriggerState> cache = newCache();
                
                List<TriggerState> states = delegate.findAllForVNodes(Set.of(vnode));
                states.forEach(state -> cache.put(state.uid(), state));
                count.addAndGet(states.size());
                return cache;
            });
        }
        LOG.info("Loaded {} trigger states for vnoded {} in {}ms", count, vNodes, System.currentTimeMillis() - start);
    }
    
    public void clear() {
        LOG.info("Clearing all trigger state caches (no assigned vNodes)");
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
