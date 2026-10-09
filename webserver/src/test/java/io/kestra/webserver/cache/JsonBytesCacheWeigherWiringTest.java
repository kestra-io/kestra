package io.kestra.webserver.cache;

import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;

import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Property;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "micronaut.caches.default.maximum-weight", value = "67108864")
class JsonBytesCacheWeigherWiringTest {

    @Inject
    private CacheManager<Object> cacheManager;

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseJsonBytesWeigherForDefaultCacheWhenMaximumWeightIsConfigured() {
        // Given
        SyncCache<Object> cache = cacheManager.getCache(JsonBytesCacheWeigher.CACHE_NAME);
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();

        // When
        nativeCache.put("key", Map.of("payload", "x".repeat(50_000)));
        nativeCache.cleanUp();

        // Then: with the singleton weigher the weighted size would be 1, with the byte weigher it tracks bytes
        long weightedSize = nativeCache.policy().eviction().orElseThrow().weightedSize().getAsLong();
        assertThat(weightedSize).isGreaterThan(50_000L);
    }
}
