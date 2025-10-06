package io.kestra.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A No-Op implementation of a Caffeine Cache.
 * Useful to disable caching but still use a cache to avoid if/else chains
 */
public class NoopCache<K, V> implements Cache<K, V> {
    private static final ConcurrentMap<?, ?> EMPTY_MAP = new ConcurrentHashMap<>(0);

    @Override
    public @Nullable V getIfPresent(K key) {
        return null;
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> mappingFunction) {
        return mappingFunction.apply(key);
    }

    @Override
    public Map<K, @NonNull V> getAllPresent(Iterable<? extends K> keys) {
        return Collections.emptyMap();
    }

    @Override
    public Map<K, @NonNull V> getAll(Iterable<? extends K> keys, Function<? super Set<? extends K>, ? extends Map<? extends K, ? extends @NonNull V>> mappingFunction) {
        return Collections.emptyMap();
    }

    @Override
    public void put(K key, @NonNull V value) {
        // just do nothing
    }

    @Override
    public void putAll(Map<? extends K, ? extends @NonNull V> map) {
        // just do nothing
    }

    @Override
    public void invalidate(K key) {
        // just do nothing
    }

    @Override
    public void invalidateAll(Iterable<? extends K> keys) {
        // just do nothing
    }

    @Override
    public void invalidateAll() {
        // just do nothing
    }

    @Override
    public long estimatedSize() {
        return 0;
    }

    @Override
    public CacheStats stats() {
        return CacheStats.empty();
    }

    @Override
    public ConcurrentMap<K, @NonNull V> asMap() {
        return (ConcurrentMap<K, V>) EMPTY_MAP;
    }

    @Override
    public void cleanUp() {
        // just do nothing
    }

    @Override
    public Policy<K, @NonNull V> policy() {
        throw new UnsupportedOperationException();
    }
}
