package io.kestra.core.docs;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Service for getting schemas.
 * <p>
 * Cached schemas are bounded: a single entry (e.g. the Flow schema over a full plugin set) can weigh tens of
 * megabytes, so entries idle for {@link #IDLE_EXPIRY} are released instead of being held for the JVM lifetime.
 */
@Singleton
public class JsonSchemaCache {

    // A schema left unread for this long is regenerated on the next request rather than kept on heap forever.
    private static final Duration IDLE_EXPIRY = Duration.ofHours(1);

    // The key domains (SchemaType x arrayOf) are small; this is a backstop should they ever grow.
    private static final long MAXIMUM_ENTRIES = 32;

    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final PluginRegistry pluginRegistry;

    private final Cache<CacheKey, Map<String, Object>> schemaCache;
    private final Cache<SchemaType, Map<String, Object>> propertiesCache;

    private final Map<SchemaType, Class<?>> classesBySchemaType = new EnumMap<>(SchemaType.class);

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
    @Inject
    public JsonSchemaCache(final JsonSchemaGenerator jsonSchemaGenerator, final PluginRegistry pluginRegistry) {
        this(jsonSchemaGenerator, pluginRegistry, Ticker.systemTicker());
    }

    // Visible for tests, so idle expiry can be exercised with a controlled ticker.
    JsonSchemaCache(final JsonSchemaGenerator jsonSchemaGenerator, final PluginRegistry pluginRegistry, final Ticker ticker) {
        this.jsonSchemaGenerator = Objects.requireNonNull(jsonSchemaGenerator, "JsonSchemaGenerator cannot be null");
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "PluginRegistry cannot be null");
        this.schemaCache = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAXIMUM_ENTRIES)
            .ticker(ticker)
            .build();
        this.propertiesCache = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAXIMUM_ENTRIES)
            .ticker(ticker)
            .build();
        registerClassForType(SchemaType.FLOW, Flow.class);
        registerClassForType(SchemaType.TASK, Task.class);
        registerClassForType(SchemaType.TRIGGER, AbstractTrigger.class);
        registerClassForType(SchemaType.DASHBOARD, Dashboard.class);
    }

    public Map<String, Object> getSchemaForType(final SchemaType type,
        final boolean arrayOf) {
        invalidateIfPluginRegistryChanged();
        return schemaCache.get(new CacheKey(type, arrayOf), key ->
        {

            Class<?> cls = Optional.ofNullable(classesBySchemaType.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Cannot found schema for type '" + type + "'"));
            return jsonSchemaGenerator.schemas(cls, arrayOf);
        });
    }

    public Map<String, Object> getPropertiesForType(final SchemaType type) {
        invalidateIfPluginRegistryChanged();
        return propertiesCache.get(type, key ->
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
        schemaCache.invalidateAll();
        propertiesCache.invalidateAll();
    }

    private record CacheKey(SchemaType type, boolean arrayOf) {
    }
}
