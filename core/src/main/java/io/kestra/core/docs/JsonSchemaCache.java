package io.kestra.core.docs;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;

import jakarta.inject.Singleton;

/**
 * Service for getting schemas.
 */
@Singleton
public class JsonSchemaCache {

    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final PluginRegistry pluginRegistry;

    private final ConcurrentMap<CacheKey, Map<String, Object>> schemaCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<SchemaType, Map<String, Object>> propertiesCache = new ConcurrentHashMap<>();

    private final Map<SchemaType, Class<?>> classesBySchemaType = new HashMap<>();

    // Hash of the plugin registry the cached schemas were generated from. When the registry changes
    // (a plugin is installed or removed at runtime) the cached schemas become stale and must be dropped,
    // otherwise the editor keeps completing/validating against a schema missing the new plugin (#12102).
    private final AtomicLong cachedRegistryHash = new AtomicLong(Long.MIN_VALUE);

    /**
     * Creates a new {@link JsonSchemaCache} instance.
     *
     * @param jsonSchemaGenerator The {@link JsonSchemaGenerator}.
     * @param pluginRegistry      The {@link PluginRegistry} whose content the cached schemas depend on.
     */
    public JsonSchemaCache(final JsonSchemaGenerator jsonSchemaGenerator, final PluginRegistry pluginRegistry) {
        this.jsonSchemaGenerator = Objects.requireNonNull(jsonSchemaGenerator, "JsonSchemaGenerator cannot be null");
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "PluginRegistry cannot be null");
        registerClassForType(SchemaType.FLOW, Flow.class);
        registerClassForType(SchemaType.TASK, Task.class);
        registerClassForType(SchemaType.TRIGGER, AbstractTrigger.class);
        registerClassForType(SchemaType.DASHBOARD, Dashboard.class);
    }

    public Map<String, Object> getSchemaForType(final SchemaType type,
        final boolean arrayOf) {
        invalidateIfPluginRegistryChanged();
        return schemaCache.computeIfAbsent(new CacheKey(type, arrayOf), key ->
        {

            Class<?> cls = Optional.ofNullable(classesBySchemaType.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Cannot found schema for type '" + type + "'"));
            return jsonSchemaGenerator.schemas(cls, arrayOf);
        });
    }

    public Map<String, Object> getPropertiesForType(final SchemaType type) {
        invalidateIfPluginRegistryChanged();
        return propertiesCache.computeIfAbsent(type, key ->
        {

            Class<?> cls = Optional.ofNullable(classesBySchemaType.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Cannot found properties for type '" + type + "'"));
            return jsonSchemaGenerator.properties(null, cls);
        });
    }

    // must be public as it's used in EE
    public void registerClassForType(final SchemaType type, final Class<?> clazz) {
        classesBySchemaType.put(type, clazz);
    }

    /**
     * Drops the cached schemas if the plugin registry changed since they were generated, so that a plugin
     * added or removed at runtime is reflected on the next request.
     */
    private void invalidateIfPluginRegistryChanged() {
        final long current = pluginRegistry.hash();
        final long previous = cachedRegistryHash.get();
        if (previous != current && cachedRegistryHash.compareAndSet(previous, current)) {
            clear();
        }
    }

    public void clear() {
        schemaCache.clear();
        propertiesCache.clear();
    }

    private record CacheKey(SchemaType type, boolean arrayOf) {
    }
}
