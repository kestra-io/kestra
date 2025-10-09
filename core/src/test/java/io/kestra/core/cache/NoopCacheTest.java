package io.kestra.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NoopCacheTest {
    private Cache<String, String> cache = new NoopCache<>();

    @Test
    void getIfPresent() {
        cache.put("key", "value");

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void get() {
        cache.put("key", "value");

        assertThat(cache.get("key", k -> "value")).isEqualTo("value");
    }

    @Test
    void getAllPresent() {
        cache.put("key", "value");

        assertThat(cache.getAllPresent(List.of("key"))).hasSize(0);
    }

    @Test
    void getAll() {
        cache.put("key", "value");

        assertThat(cache.getAll(List.of("key"), it -> Map.of("key", "value"))).hasSize(0);
    }

    @Test
    void putAll() {
        cache.putAll(Map.of("key", "value"));

        assertThat(cache.getAllPresent(List.of("key"))).hasSize(0);
    }

    @Test
    void invalidate() {
        cache.put("key", "value");
        cache.invalidate("key");

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void invalidateAll() {
        cache.putAll(Map.of("key", "value"));
        cache.invalidateAll();

        assertThat(cache.getIfPresent("key")).isNull();
    }

    @Test
    void estimatedSize() {
        cache.put("key", "value");

        assertThat(cache.estimatedSize()).isEqualTo(0);
    }

    @Test
    void stats() {
        cache.put("key", "value");

        assertThat(cache.stats()).isEqualTo(CacheStats.empty());
    }

    @Test
    void asMap() {
        cache.put("key", "value");

        assertThat(cache.asMap()).hasSize(0);
    }

    @Test
    void cleanUp() {
        cache.put("key", "value");
        cache.cleanUp();

        assertThat(cache.getAllPresent(List.of("key"))).hasSize(0);
    }

    @Test
    void policy() {
        assertThrows(UnsupportedOperationException.class, () -> cache.policy());
    }
}