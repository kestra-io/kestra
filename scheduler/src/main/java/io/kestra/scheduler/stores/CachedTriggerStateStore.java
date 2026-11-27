package io.kestra.scheduler.stores;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.vnodes.VNodes;

/**
 * A decorator class adding caching capabilities on top of {@link TriggerStateStore}.
 *
 * @see SchedulerConfiguration
 */
public class CachedTriggerStateStore implements TriggerStateStore {

    private static final Logger LOG = LoggerFactory.getLogger(CachedTriggerStateStore.class);

    private final TriggerStateStore delegate;
    private final SchedulerConfiguration schedulerConfiguration;
    private final Map<Integer, Cache<String, TriggerState>> partitionedCache = new ConcurrentHashMap<>();

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
        long start = System.currentTimeMillis();
        // The cache is size-bounded, so it cannot tell whether it holds every state of a vNode.
        List<TriggerState> states = delegate.findAllForVNodes(vNodes);
        states.forEach(state -> partitionedCache.computeIfAbsent(state.getVnode(), k -> newCache()).put(state.uid(), state));

        Map<Integer, Long> countByVNode = states.stream()
            .collect(Collectors.groupingBy(TriggerState::getVnode, TreeMap::new, Collectors.counting()));
        LOG.debug("Loaded {} trigger states for vNodes {} in {}ms, per vNode: {}", states.size(), vNodes, System.currentTimeMillis() - start, countByVNode);

        int maxSize = schedulerConfiguration.cacheMaxSizePerVNode();
        countByVNode.values().stream().max(Long::compare)
            .filter(largest -> largest > maxSize)
            .ifPresent(largest -> LOG.warn(
                "Some vNodes hold more trigger states than the scheduler cache keeps per vNode ({}), per vNode: {}. Set 'kestra.scheduler.cache-max-size-per-vnode' to at least {} to keep them all in memory.",
                maxSize, countByVNode, largest
            ));
        return states;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TriggerState> findByIdWithoutAcl(TriggerId triggerId) {
        int vnode = VNodes.computeVNodeFromTrigger(triggerId, schedulerConfiguration.vnodes());
        Cache<String, TriggerState> cache = partitionedCache.get(vnode);

        if (cache != null) {
            TriggerState cached = cache.getIfPresent(triggerId.uid());
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        Optional<TriggerState> state = delegate.findByIdWithoutAcl(triggerId);
        state.ifPresent(s ->
        {
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
    public TriggerState save(TriggerState triggerState) {
        TriggerState saved = delegate.save(triggerState);
        int vnode = triggerState.getVnode();
        partitionedCache
            .computeIfAbsent(vnode, k -> newCache())
            .put(triggerState.uid(), triggerState);
        return saved;
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

    public void clear() {
        LOG.debug("Clearing all trigger state caches (no assigned vNodes)");
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
