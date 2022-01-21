package io.kestra.core.runners.pebble;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mitchellbosecke.pebble.cache.PebbleCache;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@Slf4j
public class PebbleLruCache implements PebbleCache<Object, PebbleTemplate> {
    Cache<Object, PebbleTemplate> cache;

    public PebbleLruCache(int maximumSize) {
        cache = CacheBuilder.newBuilder()
            .initialCapacity(250)
            .maximumSize(maximumSize)
            .build();
    }

    @Override
    public PebbleTemplate computeIfAbsent(Object key, Function<? super Object, ? extends PebbleTemplate> mappingFunction) {
        try {
            return cache.get(key, () -> mappingFunction.apply(key));
        } catch (ExecutionException e) {
            log.warn("Error during cache compute", e);

            return mappingFunction.apply(key);
        }
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
