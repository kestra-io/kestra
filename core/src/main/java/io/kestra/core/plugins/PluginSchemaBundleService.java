package io.kestra.core.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.SchemaType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Version;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches and caches a pre-baked, per-release plugin schema bundle from a remote URL.
 *
 * <p>
 * Task/trigger/plugindefault/dashboard schemas overlap heavily, so the bundle (built by
 * {@code PluginsSchemaCommand}) stores their {@code definitions} once, in a single shared pool,
 * plus a {@code roots} map of {@link SchemaType} name (lower-case) → the {@code $ref} into that
 * pool for that type's root class — see {@code PluginsSchemaCommand}'s Javadoc for the exact
 * shape.
 *
 * <p>
 * This allows the editor to offer autocompletion for plugin types that are not yet installed
 * locally (KIP-45 auto-install flow). Installed-plugin schemas always take precedence when
 * {@link #mergeWithBundle(SchemaType, Map)} is called.
 *
 * <p>
 * The bundle URL template is configurable via {@code kestra.plugins.schema-bundle-url-template},
 * where {@code {version}} is replaced by the current stable Kestra version. When the property is
 * empty (the default) the service is a no-op.
 */
@Singleton
@Slf4j
public class PluginSchemaBundleService {

    private static final Duration MAX_CACHE_DURATION = Duration.ofHours(1);
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String bundleUrlTemplate;
    private final String resolvedBundleUrl;

    private CompletableFuture<Bundle> future;
    private Bundle cached = Bundle.EMPTY;
    private Instant cacheLastLoaded = Instant.now();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public PluginSchemaBundleService(
        @Nullable @Value("${kestra.plugins.schema-bundle-url-template:}") String bundleUrlTemplate) {
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
     * <p>
     * {@code JsonSchemaGenerator.schemas()} emits Draft-7 schemas: definitions live under the
     * root {@code definitions} object (not {@code $defs}, a 2019-09 keyword), and the root
     * {@code $ref} points at the polymorphic type's own definition, whose {@code anyOf} lists one
     * branch per registered subtype. The merge therefore:
     * <ul>
     * <li>copies bundle {@code definitions} entries not already present locally, keyed by FQCN
     * — the same key a given class always resolves to, whether generated from the local
     * (installed-only) or bundle (full-catalog) plugin registry;</li>
     * <li>extends every known polymorphic discriminator definition's {@code anyOf} with bundle
     * branches whose {@code $ref} isn't already referenced locally — not just the requested
     * {@code type}'s own root, but any of them found embedded in {@code localSchema} (e.g. the
     * "flow" schema embeds the same {@code Task} discriminator the "task" schema's root points
     * at), so autocompletion offers the newly-added definitions wherever they're actually used.</li>
     * </ul>
     * Both steps skip entries the local schema already has, so re-merging is idempotent and never
     * duplicates a definition or a branch. When the service is not enabled or the bundle has no
     * root registered for {@code type}, the original {@code localSchema} is returned unchanged —
     * {@code type} only gates whether merging happens at all, since a type absent from the bundle
     * (an old bundle predating a newly-added {@link SchemaType}) means the bundle is stale for it.
     *
     * @param type the schema type to look up in the bundle
     * @param localSchema the locally-generated schema (not modified)
     * @return merged schema, or the original when nothing to merge
     */
    public Map<String, Object> mergeWithBundle(SchemaType type, Map<String, Object> localSchema) {
        if (!isEnabled()) {
            return localSchema;
        }

        Bundle bundle = getBundle();
        if (bundle.roots().get(type) == null || bundle.definitions().isEmpty()) {
            return localSchema;
        }

        ObjectNode mutable = MAPPER.convertValue(localSchema, ObjectNode.class);
        mergeDefinitions(mutable, bundle.definitions());
        mergeDiscriminatorAnyOf(mutable, bundle.definitions(), bundle.roots());
        return MAPPER.convertValue(mutable, MAP_TYPE);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private synchronized Bundle getBundle() {
        if (future == null) {
            loading.set(true);
            future = CompletableFuture.supplyAsync(this::load);
        }

        try {
            Bundle result = future.get();
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

    private Bundle load() {
        try {
            log.debug("Fetching plugin schema bundle from {}", resolvedBundleUrl);
            try (InputStream in = URI.create(resolvedBundleUrl).toURL().openStream()) {
                JsonNode root = MAPPER.readTree(in);
                ObjectNode definitions = root.get("definitions") instanceof ObjectNode defs ? defs : JsonNodeFactory.instance.objectNode();

                Map<SchemaType, String> roots = new EnumMap<>(SchemaType.class);
                if (root.get("roots") instanceof ObjectNode rootsNode) {
                    rootsNode.properties().forEach(entry ->
                    {
                        try {
                            roots.put(SchemaType.fromString(entry.getKey()), entry.getValue().asText());
                        } catch (Exception e) {
                            log.debug("Ignoring unknown schema type '{}' in bundle", entry.getKey());
                        }
                    });
                }

                cacheLastLoaded = Instant.now();
                log.debug("Plugin schema bundle loaded ({} shared definitions, {} root types)", definitions.size(), roots.size());
                return new Bundle(definitions, roots);
            }
        } catch (IOException e) {
            log.warn("Could not fetch plugin schema bundle from '{}': {}", resolvedBundleUrl, e.getMessage());
            return Bundle.EMPTY;
        } finally {
            loading.set(false);
        }
    }

    private static void mergeDefinitions(ObjectNode local, ObjectNode sharedDefinitions) {
        JsonNode localDefs = local.get("definitions");
        ObjectNode targetDefs = localDefs instanceof ObjectNode existing ? existing : local.putObject("definitions");

        sharedDefinitions.properties().forEach(entry ->
        {
            if (!targetDefs.has(entry.getKey())) {
                targetDefs.set(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Extends the {@code anyOf} of every known polymorphic discriminator definition present in
     * {@code local} — whether it's the schema's own root (e.g. requesting the "task" schema
     * directly) or a definition merely embedded within it (e.g. {@code Task} nested inside the
     * "flow" schema's {@code tasks} property) — with bundle branches missing locally.
     * {@code schemas()} never puts {@code anyOf} at the schema root itself, only on the
     * definition its {@code $ref} points at, so this walks every {@link SchemaType} root key the
     * bundle knows about rather than just the one matching the requested type: the "flow" schema
     * has no {@code anyOf} of its own, but its {@code definitions} pool still contains the same
     * {@code Task} discriminator entry the "task" schema's root points at.
     */
    private static void mergeDiscriminatorAnyOf(ObjectNode local, ObjectNode sharedDefinitions, Map<SchemaType, String> bundleRoots) {
        JsonNode localDefs = local.get("definitions");
        if (!(localDefs instanceof ObjectNode localDefinitions)) {
            return;
        }

        bundleRoots.values().forEach(bundleRootRef ->
        {
            String key = definitionKeyFromRef(bundleRootRef);
            ObjectNode localEntry = definitionEntry(localDefinitions, key);
            ObjectNode bundleEntry = definitionEntry(sharedDefinitions, key);
            if (localEntry == null || bundleEntry == null) {
                return;
            }

            JsonNode bundleAnyOf = bundleEntry.get("anyOf");
            if (!(bundleAnyOf instanceof ArrayNode bundleBranches)) {
                return;
            }

            ArrayNode targetAnyOf = localEntry.get("anyOf") instanceof ArrayNode existing ? existing : localEntry.putArray("anyOf");

            Set<String> existingRefs = new HashSet<>();
            targetAnyOf.forEach(branch ->
            {
                JsonNode ref = branch.get("$ref");
                if (ref != null) {
                    existingRefs.add(ref.asText());
                }
            });

            bundleBranches.forEach(branch ->
            {
                JsonNode ref = branch.get("$ref");
                if (ref != null && existingRefs.add(ref.asText())) {
                    targetAnyOf.add(branch);
                }
            });
        });
    }

    private static String definitionKeyFromRef(String ref) {
        return ref == null ? null : ref.substring(ref.lastIndexOf('/') + 1);
    }

    private static ObjectNode definitionEntry(JsonNode definitions, String key) {
        if (!(definitions instanceof ObjectNode defs) || !(defs.get(key) instanceof ObjectNode entry)) {
            return null;
        }
        return entry;
    }

    /** The bundle's shared {@code definitions} pool plus each {@link SchemaType}'s root {@code $ref} into it. */
    private record Bundle(ObjectNode definitions, Map<SchemaType, String> roots) {
        static final Bundle EMPTY = new Bundle(JsonNodeFactory.instance.objectNode(), Map.of());

        boolean isEmpty() {
            return roots.isEmpty();
        }
    }
}
