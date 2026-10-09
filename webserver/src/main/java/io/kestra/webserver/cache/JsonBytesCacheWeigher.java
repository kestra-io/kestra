package io.kestra.webserver.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Weigher;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Weighs entries of the {@code default} Micronaut cache by their serialized JSON size in bytes, so that
 * {@code micronaut.caches.default.maximum-weight} is an actual byte bound.
 * <p>
 * Without a {@link Weigher} bean named after the cache, Caffeine falls back to
 * {@link Weigher#singletonWeigher()} which counts every entry as 1, turning the configured
 * maximum weight into an entry count instead of a byte budget.
 */
@Slf4j
@Singleton
@Named(JsonBytesCacheWeigher.CACHE_NAME)
public class JsonBytesCacheWeigher implements Weigher<Object, Object> {

    public static final String CACHE_NAME = "default";

    // Entries that cannot be serialized fall back to a fixed weight instead of failing the cache write.
    static final int FALLBACK_WEIGHT = 1024;

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Override
    public int weigh(final Object key, final Object value) {
        try {
            CountingOutputStream out = new CountingOutputStream();
            MAPPER.writeValue(out, value);
            return (int) Math.min(out.count, Integer.MAX_VALUE);
        } catch (IOException e) {
            log.warn("Cannot weigh cache entry of type '{}': falling back to a weight of {} bytes.",
                value == null ? "null" : value.getClass().getName(), FALLBACK_WEIGHT, e);
            return FALLBACK_WEIGHT;
        }
    }

    /**
     * Counts written bytes without buffering them, so weighing a multi-megabyte entry allocates nothing.
     */
    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(final int b) {
            count++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            count += len;
        }
    }
}
