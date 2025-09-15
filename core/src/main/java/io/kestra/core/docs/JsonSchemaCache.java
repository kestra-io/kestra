package io.kestra.core.docs;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.AbstractTrigger;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for getting schemas.
 */
@Singleton
public class JsonSchemaCache {

    private final JsonSchemaGenerator jsonSchemaGenerator;

    private final ConcurrentMap<CacheKey, Map<String, Object>> schemaCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<SchemaType, Map<String, Object>> propertiesCache = new ConcurrentHashMap<>();

    private final Map<SchemaType, Class<?>> classesBySchemaType = new HashMap<>();

    /**
     * Creates a new {@link JsonSchemaCache} instance.
     *
     * @param jsonSchemaGenerator The {@link JsonSchemaGenerator}.
     */
    public JsonSchemaCache(final JsonSchemaGenerator jsonSchemaGenerator) {
        this.jsonSchemaGenerator = Objects.requireNonNull(jsonSchemaGenerator, "JsonSchemaGenerator cannot be null");
        registerClassForType(SchemaType.FLOW, Flow.class);
        registerClassForType(SchemaType.TEMPLATE, Template.class);
        registerClassForType(SchemaType.TASK, Task.class);
        registerClassForType(SchemaType.TRIGGER, AbstractTrigger.class);
        registerClassForType(SchemaType.PLUGINDEFAULT, PluginDefault.class);
        registerClassForType(SchemaType.DASHBOARD, Dashboard.class);
    }

    public Map<String, Object> getSchemaForType(final SchemaType type,
                                                final boolean arrayOf) {
        return schemaCache.computeIfAbsent(new CacheKey(type, arrayOf), key -> {

            Class<?> cls = Optional.ofNullable(classesBySchemaType.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Cannot found schema for type '" + type + "'"));
            return jsonSchemaGenerator.schemas(cls, arrayOf);
        });
    }

    public Map<String, Object> getPropertiesForType(final SchemaType type) {
        return propertiesCache.computeIfAbsent(type, key -> {

            Class<?> cls = Optional.ofNullable(classesBySchemaType.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Cannot found properties for type '" + type + "'"));
            return jsonSchemaGenerator.properties(null, cls);
        });
    }

    // must be public as it's used in EE
    public void registerClassForType(final SchemaType type, final Class<?> clazz) {
        classesBySchemaType.put(type, clazz);
    }

    public void clear() {
        schemaCache.clear();
    }

    private record CacheKey(SchemaType type, boolean arrayOf) {
    }
}
