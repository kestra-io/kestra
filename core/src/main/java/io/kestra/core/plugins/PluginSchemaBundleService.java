package io.kestra.core.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
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
     * Returns a copy of {@code localSchema} enriched with lightweight type entries for {@code type}.
     *
     * <p>
     * {@code JsonSchemaGenerator.schemas()} emits Draft-7 schemas: definitions live under the root
     * {@code definitions} object (not {@code $defs}, a 2019-09 keyword), and each polymorphic
     * discriminator definition's {@code anyOf} lists one branch per registered subtype, each branch
     * carrying a {@code type} {@code const} discriminator.
     *
     * <p>
     * Autocompletion of a task/trigger {@code type} only needs that {@code const} — not the plugin's
     * full property schema. So rather than copying the bundle's (multi-MB) definitions pool into the
     * response, the merge adds, for each catalog subtype not already installed locally, a
     * <em>lightweight definition</em> ({@code {type: object, properties: {type: {const: <fqcn>}},
     * required: [type]}}, plus {@code title}/{@code markdownDescription} when present) and a
     * {@code $ref} branch to it in the discriminator's {@code anyOf}. This mirrors the exact shape of
     * an installed subtype (the editor's YAML language service only offers a {@code type} const from an
     * {@code anyOf} branch that resolves to an object definition — an inline, type-less stub is
     * skipped), while omitting the heavy property schema keeps the response small enough for the
     * browser worker (the full-catalog pool of thousands of types would otherwise balloon it).
     * Property-level completion for a given plugin arrives once it is actually installed and its full
     * definition enters {@code localSchema}.
     *
     * <p>
     * It walks every discriminator the bundle knows about — not just the requested {@code type}'s own
     * root, but any occurrence embedded in {@code localSchema}, including subtype lists the generator
     * inlined directly at property sites (e.g. {@code Flow.tasks.items.anyOf}) — see
     * {@link #mergeLightweightSubtypes}. Dedup is by FQCN, so an installed subtype (already an
     * {@code anyOf} branch) is never shadowed by a stub and re-merging is idempotent. When the
     * service is disabled or no bundle could be loaded, the original {@code localSchema} is returned
     * unchanged.
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
        if (bundle.isEmpty() || bundle.definitions().isEmpty()) {
            return localSchema;
        }

        ObjectNode mutable = MAPPER.convertValue(localSchema, ObjectNode.class);
        mergeLightweightSubtypes(mutable, bundle.definitions(), bundle.roots());
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

    /**
     * For every polymorphic subtype list present in {@code local}, adds, for each catalog subtype
     * not already installed locally, a <b>lightweight definition</b> plus a {@code $ref} branch
     * pointing at it. The added entry mirrors the exact shape of an installed subtype (a definition
     * with {@code type: object} + a {@code type} {@code const}, referenced from the site's
     * {@code anyOf}) — only without the plugin's full property schema. That structural parity
     * matters: the editor's YAML language service offers a {@code type} value from an {@code anyOf}
     * branch that resolves to an object definition; an inline branch without {@code type: object}
     * is silently skipped, which is why a type-only stub failed to autocomplete.
     *
     * <p>
     * Crucially, the generator does <em>not</em> route every subtype list through the discriminator
     * base-class definition: the "flow" schema inlines the full installed-subtype {@code anyOf}
     * directly at each property site ({@code Flow.tasks.items}, {@code errors}, every flowable
     * task's nested {@code tasks}, …) while the {@code Task} definition itself is barely referenced.
     * Patching only the named discriminator definition therefore never reaches what the editor
     * actually completes from. Instead, every {@code anyOf} array in {@code local} whose {@code $ref}
     * branch keys intersect a bundle discriminator's subtype set is treated as an occurrence of that
     * discriminator and extended. This is a heuristic, but a safe one: the generator always emits the
     * <em>full</em> registered subtype list at such sites (verified against a real flow schema — every
     * intersecting site carried the complete installed set), and subtype sets of distinct
     * discriminators (task/trigger/…) are disjoint. Dedup is by FQCN per site, so an installed
     * subtype is never shadowed and re-merging is idempotent.
     */
    private static void mergeLightweightSubtypes(ObjectNode local, ObjectNode bundleDefinitions, Map<SchemaType, String> bundleRoots) {
        JsonNode localDefsNode = local.get("definitions");
        ObjectNode localDefinitions = localDefsNode instanceof ObjectNode existing ? existing : local.putObject("definitions");

        List<ArrayNode> anyOfSites = new ArrayList<>();
        collectAnyOfSites(local, anyOfSites);

        bundleRoots.values().forEach(bundleRootRef ->
        {
            ObjectNode bundleEntry = definitionEntry(bundleDefinitions, definitionKeyFromRef(bundleRootRef));
            if (bundleEntry == null || !(bundleEntry.get("anyOf") instanceof ArrayNode bundleBranches)) {
                return;
            }

            Set<String> bundleSubtypes = refKeys(bundleBranches);
            if (bundleSubtypes.isEmpty()) {
                return;
            }

            // The named discriminator definition, when present locally, is always an occurrence —
            // even with an empty (or missing) anyOf, where the intersection heuristic can't see it.
            ObjectNode localEntry = definitionEntry(localDefinitions, definitionKeyFromRef(bundleRootRef));
            if (localEntry != null) {
                ArrayNode namedAnyOf = localEntry.get("anyOf") instanceof ArrayNode existing ? existing : localEntry.putArray("anyOf");
                extendSite(namedAnyOf, bundleSubtypes, localDefinitions, bundleDefinitions);
            }

            anyOfSites.forEach(site ->
            {
                if (Collections.disjoint(refKeys(site), bundleSubtypes)) {
                    return;
                }
                extendSite(site, bundleSubtypes, localDefinitions, bundleDefinitions);
            });
        });
    }

    /** Appends a {@code $ref} branch (and its lightweight definition when missing) for every bundle subtype not already listed at {@code site}. */
    private static void extendSite(ArrayNode site, Set<String> bundleSubtypes, ObjectNode localDefinitions, ObjectNode bundleDefinitions) {
        Set<String> existingSubtypes = refKeys(site);
        bundleSubtypes.forEach(subtypeKey ->
        {
            if (!existingSubtypes.add(subtypeKey)) {
                return;
            }
            if (!localDefinitions.has(subtypeKey)) {
                localDefinitions.set(subtypeKey, lightweightDefinition(subtypeKey, definitionEntry(bundleDefinitions, subtypeKey)));
            }
            site.add(JsonNodeFactory.instance.objectNode().put("$ref", "#/definitions/" + subtypeKey));
        });
    }

    /** Collects every {@code anyOf} array node in the tree (before any mutation, so patching sites can't re-trigger traversal). */
    private static void collectAnyOfSites(JsonNode node, List<ArrayNode> sites) {
        if (node instanceof ObjectNode obj) {
            obj.properties().forEach(entry ->
            {
                if ("anyOf".equals(entry.getKey()) && entry.getValue() instanceof ArrayNode anyOf) {
                    sites.add(anyOf);
                }
                collectAnyOfSites(entry.getValue(), sites);
            });
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(child -> collectAnyOfSites(child, sites));
        }
    }

    /** Returns the definition keys of every {@code $ref} branch in {@code anyOf} (insertion-ordered). */
    private static Set<String> refKeys(ArrayNode anyOf) {
        Set<String> keys = new LinkedHashSet<>();
        anyOf.forEach(branch ->
        {
            JsonNode ref = branch.get("$ref");
            if (ref != null && ref.isTextual()) {
                keys.add(definitionKeyFromRef(ref.asText()));
            }
        });
        return keys;
    }

    /**
     * Builds a minimal object definition that only pins the discriminator {@code type} to the
     * subtype's FQCN (plus its {@code title}/{@code markdownDescription} for the completion popup when
     * the bundle carries them). Includes {@code type: object} so it matches the shape of an installed
     * subtype definition — the editor won't offer a {@code type} const from a branch that isn't an
     * object schema.
     */
    private static ObjectNode lightweightDefinition(String fqcn, @Nullable ObjectNode bundleDefinition) {
        String typeConst = fqcn;
        if (bundleDefinition != null && bundleDefinition.path("properties").path("type").path("const").isTextual()) {
            typeConst = bundleDefinition.path("properties").path("type").path("const").asText();
        }

        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("type", "object");
        definition.putObject("properties").putObject("type").put("const", typeConst);
        definition.putArray("required").add("type");
        if (bundleDefinition != null) {
            copyText(bundleDefinition, definition, "title");
            copyText(bundleDefinition, definition, "markdownDescription");
        }
        return definition;
    }

    private static void copyText(ObjectNode from, ObjectNode to, String field) {
        JsonNode value = from.get(field);
        if (value != null && value.isTextual()) {
            to.set(field, value);
        }
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
