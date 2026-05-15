package io.kestra.core.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.serializers.JacksonMapper;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.SchemaType;
import io.kestra.core.utils.Version;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches and caches a pre-baked, per-release plugin schema bundle from a remote URL.
 *
 * <p>The bundle is a JSON object keyed by {@link SchemaType} name (lower-case), where each
 * value is a full Draft-7 JSON Schema covering all tasks/triggers shipped with the official
 * full-plugin Kestra image for the current release.
 *
 * <p>This allows the editor to offer autocompletion for plugin types that are not yet installed
 * locally (KIP-45 auto-install flow). Installed-plugin schemas always take precedence when
 * {@link #mergeWithBundle(SchemaType, Map)} is called.
 *
 * <p>The bundle URL template is configurable via {@code kestra.plugins.schema-bundle-url-template},
 * where {@code {version}} is replaced by the current stable Kestra version. When the property is
 * empty (the default) the service is a no-op.
 */
@Singleton
@Slf4j
public class PluginSchemaBundleService {

    private static final Duration MAX_CACHE_DURATION = Duration.ofHours(1);
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, JsonNode>> BUNDLE_TYPE = new TypeReference<>() {};

    private final String bundleUrlTemplate;
    private final String resolvedBundleUrl;

    private CompletableFuture<Map<SchemaType, JsonNode>> future;
    private Map<SchemaType, JsonNode> cached = Map.of();
    private Instant cacheLastLoaded = Instant.now();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public PluginSchemaBundleService(
        @Nullable @Value("${kestra.plugins.schema-bundle-url-template:}") String bundleUrlTemplate
    ) {
        this.bundleUrlTemplate = bundleUrlTemplate == null ? "" : bundleUrlTemplate;
        if (this.bundleUrlTemplate.isEmpty()) {
            this.resolvedBundleUrl = "";
        } else {
            Version version = Version.of(KestraContext.getContext().getVersion());
            String stable = new Version(version.majorVersion(), version.minorVersion(), version.patchVersion(), null).toString();
            this.resolvedBundleUrl = this.bundleUrlTemplate.replace("{version}", stable);
        }
    }

    /**
     * Returns whether this service has a bundle URL configured and is therefore active.
     */
    public boolean isEnabled() {
        return !resolvedBundleUrl.isEmpty();
    }

    /**
     * Returns a copy of {@code localSchema} enriched with bundle entries for {@code type}.
     *
     * <p>The merge adds only entries that are absent from the local schema:
     * <ul>
     *   <li>{@code $defs} — bundle keys not present in the local {@code $defs} are added.</li>
     *   <li>{@code anyOf} — bundle refs not already referenced by a local entry are appended.</li>
     * </ul>
     * When the service is not enabled or no bundle data is available for {@code type}, the original
     * {@code localSchema} is returned unchanged.
     *
     * @param type        the schema type to look up in the bundle
     * @param localSchema the locally-generated schema (not modified)
     * @return merged schema, or the original when nothing to merge
     */
    public Map<String, Object> mergeWithBundle(SchemaType type, Map<String, Object> localSchema) {
        if (!isEnabled()) {
            return localSchema;
        }

        JsonNode bundleNode = getBundle().get(type);
        if (bundleNode == null || !bundleNode.isObject()) {
            return localSchema;
        }

        ObjectNode mutable = MAPPER.convertValue(localSchema, ObjectNode.class);
        mergeDefs(mutable, (ObjectNode) bundleNode);
        mergeAnyOf(mutable, (ObjectNode) bundleNode);
        return MAPPER.convertValue(mutable, MAP_TYPE);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private synchronized Map<SchemaType, JsonNode> getBundle() {
        if (future == null) {
            loading.set(true);
            future = CompletableFuture.supplyAsync(this::load);
        }

        try {
            Map<SchemaType, JsonNode> result = future.get();
            if (!result.isEmpty()) {
                cached = result;
            }
            if (cacheLastLoaded.plus(MAX_CACHE_DURATION).isBefore(Instant.now())) {
                if (loading.compareAndSet(false, true)) {
                    future = CompletableFuture.supplyAsync(this::load);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Failed to load plugin schema bundle from '{}': {}", resolvedBundleUrl, cause.getMessage());
        }

        return cached;
    }

    private Map<SchemaType, JsonNode> load() {
        try {
            log.debug("Fetching plugin schema bundle from {}", resolvedBundleUrl);
            try (InputStream in = URI.create(resolvedBundleUrl).toURL().openStream()) {
                Map<String, JsonNode> raw = MAPPER.readValue(in, BUNDLE_TYPE);
                Map<SchemaType, JsonNode> result = new EnumMap<>(SchemaType.class);
                raw.forEach((key, value) -> {
                    try {
                        result.put(SchemaType.fromString(key), value);
                    } catch (Exception e) {
                        log.debug("Ignoring unknown schema type '{}' in bundle", key);
                    }
                });
                cacheLastLoaded = Instant.now();
                log.debug("Plugin schema bundle loaded ({} types)", result.size());
                return result;
            }
        } catch (IOException e) {
            log.warn("Could not fetch plugin schema bundle from '{}': {}", resolvedBundleUrl, e.getMessage());
            return Map.of();
        } finally {
            loading.set(false);
        }
    }

    private static void mergeDefs(ObjectNode local, ObjectNode bundle) {
        JsonNode localDefs = local.get("$defs");
        JsonNode bundleDefs = bundle.get("$defs");
        if (bundleDefs == null || !bundleDefs.isObject()) {
            return;
        }

        ObjectNode targetDefs;
        if (localDefs == null || !localDefs.isObject()) {
            targetDefs = local.putObject("$defs");
        } else {
            targetDefs = (ObjectNode) localDefs;
        }

        bundleDefs.fields().forEachRemaining(entry -> {
            if (!targetDefs.has(entry.getKey())) {
                targetDefs.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private static void mergeAnyOf(ObjectNode local, ObjectNode bundle) {
        JsonNode bundleAnyOf = bundle.get("anyOf");
        if (bundleAnyOf == null || !bundleAnyOf.isArray()) {
            return;
        }

        ArrayNode targetAnyOf;
        JsonNode localAnyOf = local.get("anyOf");
        if (localAnyOf == null || !localAnyOf.isArray()) {
            targetAnyOf = local.putArray("anyOf");
        } else {
            targetAnyOf = (ArrayNode) localAnyOf;
        }

        // Collect existing $ref values to detect duplicates efficiently.
        java.util.Set<String> existingRefs = new java.util.HashSet<>();
        targetAnyOf.forEach(node -> {
            JsonNode ref = node.get("$ref");
            if (ref != null) {
                existingRefs.add(ref.asText());
            }
        });

        bundleAnyOf.forEach(node -> {
            JsonNode ref = node.get("$ref");
            if (ref != null && !existingRefs.contains(ref.asText())) {
                targetAnyOf.add(node);
                existingRefs.add(ref.asText());
            }
        });
    }
}
