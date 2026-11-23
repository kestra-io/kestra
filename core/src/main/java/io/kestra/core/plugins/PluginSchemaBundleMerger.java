package io.kestra.core.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.docs.SchemaType;

import jakarta.annotation.Nullable;

/**
 * Merges the pre-baked plugin schema bundle into a locally-generated Draft-7 schema, adding a
 * lightweight definition plus a {@code $ref} branch for every catalog subtype not installed
 * locally, so the editor can complete types (and their property names) without the plugin JAR.
 * Pure functions over Jackson nodes; the loading/caching of the bundle lives in
 * {@link PluginSchemaBundleService}.
 */
final class PluginSchemaBundleMerger {

    // Appended to every stub's doc so the editor signals, while typing, that the type is not
    // installed; accurate whether or not auto-install is enabled on the instance.
    private static final String STUB_NOTE =
        "**Plugin not installed.** With plugin auto-install enabled, it is installed automatically when the flow is saved.";

    private PluginSchemaBundleMerger() {
    }

    /**
     * For every polymorphic subtype list present in {@code local}, adds, for each catalog subtype
     * not already installed locally, a lightweight definition plus a {@code $ref} branch pointing
     * at it. The added entry mirrors the exact shape of an installed subtype (a {@code type: object}
     * definition with a {@code type} {@code const}, referenced from the site's {@code anyOf}) — the
     * editor's YAML language service silently skips inline, type-less stubs — while omitting the
     * plugin's full property schema to keep the response small enough for the browser worker.
     *
     * <p>
     * The generator inlines the full installed-subtype {@code anyOf} directly at property sites
     * ({@code Flow.tasks.items}, {@code errors}, nested {@code tasks}, …) rather than routing them
     * through the discriminator base-class definition, so patching only the named definition never
     * reaches what the editor completes from. Instead, every {@code anyOf} array whose {@code $ref}
     * keys intersect a bundle discriminator's subtype set is extended — safe because the generator
     * always emits the full registered subtype list at such sites and the subtype sets of distinct
     * discriminators are disjoint. Dedup is by FQCN per site, so an installed subtype is never
     * shadowed and re-merging is idempotent.
     */
    static void mergeLightweightSubtypes(ObjectNode local, ObjectNode bundleDefinitions, Map<SchemaType, String> bundleRoots) {
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
     * Builds a minimal object definition pinning the discriminator {@code type} to the subtype's
     * FQCN, carrying over {@code title}/{@code markdownDescription} and {@code required} when the
     * bundle has them. Other properties are copied by name only — {@code {title?,
     * markdownDescription?}} shells with no type or nested schema — enough for key completion under
     * a not-yet-installed task while omitting the heavy nested schemas.
     */
    private static ObjectNode lightweightDefinition(String fqcn, @Nullable ObjectNode bundleDefinition) {
        String typeConst = fqcn;
        if (bundleDefinition != null && bundleDefinition.path("properties").path("type").path("const").isTextual()) {
            typeConst = bundleDefinition.path("properties").path("type").path("const").asText();
        }

        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.put("type", "object");
        ObjectNode properties = definition.putObject("properties");
        properties.putObject("type").put("const", typeConst);

        ArrayNode required = definition.putArray("required");
        required.add("type");

        if (bundleDefinition != null) {
            copyText(bundleDefinition, definition, "title");
            copyText(bundleDefinition, definition, "markdownDescription");

            if (bundleDefinition.get("properties") instanceof ObjectNode bundleProperties) {
                bundleProperties.properties().forEach(entry ->
                {
                    if (properties.has(entry.getKey())) {
                        return;
                    }
                    ObjectNode shell = properties.putObject(entry.getKey());
                    if (entry.getValue() instanceof ObjectNode bundleProperty) {
                        copyText(bundleProperty, shell, "title");
                        copyText(bundleProperty, shell, "markdownDescription");
                    }
                });
            }

            if (bundleDefinition.get("required") instanceof ArrayNode bundleRequired) {
                Set<String> present = new LinkedHashSet<>();
                required.forEach(name -> present.add(name.asText()));
                bundleRequired.forEach(name ->
                {
                    if (name.isTextual() && present.add(name.asText())) {
                        required.add(name);
                    }
                });
            }
        }

        JsonNode doc = definition.get("markdownDescription");
        definition.put("markdownDescription", doc != null && doc.isTextual() ? doc.asText() + "\n\n" + STUB_NOTE : STUB_NOTE);
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
}
