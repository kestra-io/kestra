package io.kestra.core.docs;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

import io.kestra.core.plugins.PluginRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonSchemaCacheTest {

    @Test
    void shouldServeFromCacheWhenPluginRegistryUnchanged() {
        // Given
        JsonSchemaGenerator generator = mock(JsonSchemaGenerator.class);
        PluginRegistry registry = mock(PluginRegistry.class);
        when(generator.schemas(any(), anyBoolean())).thenReturn(Map.of("version", "1"));
        when(registry.hash()).thenReturn(1L);
        JsonSchemaCache cache = new JsonSchemaCache(generator, registry);

        // When
        cache.getSchemaForType(SchemaType.FLOW, false);
        cache.getSchemaForType(SchemaType.FLOW, false);

        // Then: the schema is generated only once while the registry hash is stable
        verify(generator, times(1)).schemas(any(), anyBoolean());
    }

    @Test
    void shouldRebuildSchemaWhenPluginRegistryChanges() {
        // Given: a registry whose content (hash) changes between calls, e.g. a plugin installed at runtime
        JsonSchemaGenerator generator = mock(JsonSchemaGenerator.class);
        PluginRegistry registry = mock(PluginRegistry.class);
        when(generator.schemas(any(), anyBoolean()))
            .thenReturn(Map.of("version", "1"))
            .thenReturn(Map.of("version", "2"));
        when(registry.hash()).thenReturn(1L, 1L, 2L);
        JsonSchemaCache cache = new JsonSchemaCache(generator, registry);

        // When
        Map<String, Object> first = cache.getSchemaForType(SchemaType.FLOW, false);
        Map<String, Object> cached = cache.getSchemaForType(SchemaType.FLOW, false);
        Map<String, Object> afterChange = cache.getSchemaForType(SchemaType.FLOW, false);

        // Then: unchanged hash serves the cached schema, changed hash regenerates it
        assertThat(first).containsEntry("version", "1");
        assertThat(cached).containsEntry("version", "1");
        assertThat(afterChange).containsEntry("version", "2");
        verify(generator, times(2)).schemas(any(), anyBoolean());
    }

    @Test
    void shouldRegenerateSchemaWhenEntryWasIdleLongerThanExpiry() {
        // Given: a controlled clock so idle expiry can be simulated
        JsonSchemaGenerator generator = mock(JsonSchemaGenerator.class);
        PluginRegistry registry = mock(PluginRegistry.class);
        when(generator.schemas(any(), anyBoolean())).thenReturn(Map.of("version", "1"));
        when(registry.hash()).thenReturn(1L);
        AtomicLong nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        JsonSchemaCache cache = new JsonSchemaCache(generator, registry, ticker);

        // When: a second read within the idle window, then a third one after two hours of idleness
        cache.getSchemaForType(SchemaType.FLOW, false);
        nanos.addAndGet(Duration.ofMinutes(30).toNanos());
        cache.getSchemaForType(SchemaType.FLOW, false);
        nanos.addAndGet(Duration.ofHours(2).toNanos());
        cache.getSchemaForType(SchemaType.FLOW, false);

        // Then: the idle entry is released and regenerated on the next request
        verify(generator, times(2)).schemas(any(), anyBoolean());
    }
}
