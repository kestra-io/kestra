package io.kestra.core.runners.pebble;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.pebbletemplates.pebble.cache.PebbleCache;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.function.Function;

public class PebbleLruCache implements PebbleCache<Object, PebbleTemplate> {
    private final Cache<Object, PebbleTemplate> cache;

    public PebbleLruCache(int maximumSize) {
        cache = Caffeine.newBuilder()
            .initialCapacity(250)
            .maximumSize(maximumSize)
            .recordStats()
            .build();
    }

    @Override
    public PebbleTemplate computeIfAbsent(Object key, Function<? super Object, ? extends PebbleTemplate> mappingFunction) {
        try {
            return cache.get(key, mappingFunction);
        } catch (Exception e) {
            // we retry the mapping function in order to let the exception be thrown instead of being capture by cache
            return mappingFunction.apply(key);
        }
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void register(MeterRegistry meterRegistry) {
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "pebble-template");
    }
}
