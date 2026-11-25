package io.kestra.core.docs;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.kestra.core.serializers.JacksonMapper;

/** Replaces repeated subtrees with a {@code $ref} to one {@code shared-*} definition, which the UI's {@code inflateCompactSchema} undoes. */
public final class JsonSchemaCompactor {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFINITIONS_REF = "#/definitions/";
    private static final String SHARED_KEY_PREFIX = "shared-property-";
    private static final String SHARED_SUBTYPES_KEY = "shared-subtypes";
    // Below this serialized size, the $ref and its definition key cost about as much as the copy they replace.
    private static final int MIN_SHARED_LENGTH = 128;

    private JsonSchemaCompactor() {
    }

    /** Returns a compacted copy of {@code schema}: examples dropped, subtype lists hoisted, then repeated property schemas shared. */
    public static Map<String, Object> compact(Map<String, Object> schema) {
        ObjectNode root = MAPPER.convertValue(schema, ObjectNode.class);
        dropExamples(root);
        hoistSubtypeLists(root);
        sharePropertySchemas(root);
        return MAPPER.convertValue(root, MAP_TYPE);
    }

    /** Safe to run again on a schema that gained definitions since, as a property already behind a {@code $ref} is left alone. */
    public static void sharePropertySchemas(ObjectNode schema) {
        if (!(schema.get("definitions") instanceof ObjectNode definitions)) {
            return;
        }

        Map<String, String> refByContent = new HashMap<>();
        Set<String> usedKeys = new HashSet<>();
        definitions.fieldNames().forEachRemaining(usedKeys::add);

        // Sharing a schema leaves a single copy of whatever it contains, so occurrences are counted again until nothing repeats.
        Map<String, ObjectNode> added;
        do {
            Map<String, Integer> occurrences = new HashMap<>();
            walkProperties(schema, (name, property) ->
            {
                occurrences.merge(property.toString(), 1, Integer::sum);
                return true;
            });

            added = new LinkedHashMap<>();
            Map<String, ObjectNode> newDefinitions = added;
            walkProperties(schema, (name, property) ->
            {
                String content = property.toString();
                if (content.length() < MIN_SHARED_LENGTH || (occurrences.get(content) < 2 && !refByContent.containsKey(content))) {
                    return true;
                }
                String ref = refByContent.computeIfAbsent(content, ignored ->
                {
                    String key = uniqueKey(SHARED_KEY_PREFIX + name, usedKeys);
                    newDefinitions.put(key, property.deepCopy());
                    return DEFINITIONS_REF + key;
                });
                property.removeAll().put("$ref", ref);
                return false;
            });
            definitions.setAll(added);
        } while (!added.isEmpty());
    }

    private static void hoistSubtypeLists(ObjectNode schema) {
        if (!(schema.get("definitions") instanceof ObjectNode definitions)) {
            return;
        }

        Map<JsonNode, String> holderBySubtypes = new HashMap<>();
        Set<JsonNode> hoisted = Collections.newSetFromMap(new IdentityHashMap<>());
        definitions.properties().forEach(entry ->
        {
            if (SubtypeSite.of(entry.getValue()) instanceof SubtypeSite site && site.annotation() == null
                && holderBySubtypes.putIfAbsent(site.subtypes(), DEFINITIONS_REF + entry.getKey()) == null) {
                hoisted.add(entry.getValue());
            }
        });

        Map<JsonNode, Integer> occurrences = new LinkedHashMap<>();
        countSubtypeLists(schema, hoisted, occurrences);
        Set<String> usedKeys = new HashSet<>();
        definitions.fieldNames().forEachRemaining(usedKeys::add);
        Map<JsonNode, String> refBySubtypes = new HashMap<>();
        Map<String, ObjectNode> added = new LinkedHashMap<>();
        occurrences.forEach((subtypes, count) ->
        {
            // A site never references a holding definition directly: other properties do, and a shared-* key is what marks a hoisted list.
            if (holderBySubtypes.get(subtypes) instanceof String holder) {
                String key = uniqueKey(SHARED_SUBTYPES_KEY, usedKeys);
                added.put(key, MAPPER.createObjectNode().put("$ref", holder));
                refBySubtypes.put(subtypes, DEFINITIONS_REF + key);
            } else if (count >= 2 && subtypes.toString().length() >= MIN_SHARED_LENGTH) {
                String key = uniqueKey(SHARED_SUBTYPES_KEY, usedKeys);
                added.put(key, MAPPER.createObjectNode().set("anyOf", subtypes));
                refBySubtypes.put(subtypes, DEFINITIONS_REF + key);
            }
        });

        if (!refBySubtypes.isEmpty()) {
            replaceSubtypeLists(schema, refBySubtypes, hoisted);
            definitions.setAll(added);
        }
    }

    private static void countSubtypeLists(JsonNode node, Set<JsonNode> hoisted, Map<JsonNode, Integer> occurrences) {
        if (hoisted.contains(node)) {
            return;
        }
        if (SubtypeSite.of(node) instanceof SubtypeSite site) {
            occurrences.merge(site.subtypes(), 1, Integer::sum);
            return;
        }
        node.forEach(child -> countSubtypeLists(child, hoisted, occurrences));
    }

    private static void replaceSubtypeLists(JsonNode node, Map<JsonNode, String> refBySubtypes, Set<JsonNode> hoisted) {
        if (hoisted.contains(node)) {
            return;
        }
        if (SubtypeSite.of(node) instanceof SubtypeSite site) {
            if (refBySubtypes.get(site.subtypes()) instanceof String ref) {
                ObjectNode replaced = ((ObjectNode) node).removeAll();
                if (site.annotation() == null || site.annotation().isEmpty()) {
                    replaced.put("$ref", ref);
                } else {
                    // Kept as a one-branch anyOf beside the annotation: the language service then completes the key and hovers it as before.
                    replaced.setAll(site.annotation());
                    replaced.putArray("anyOf").addObject().put("$ref", ref);
                }
            }
            return;
        }
        node.forEach(child -> replaceSubtypeLists(child, refBySubtypes, hoisted));
    }

    /** One branch per subtype, either a bare {@code $ref} or the generator's {@code allOf: [$ref, annotation]} for a single-subtype property. */
    private record SubtypeSite(ArrayNode subtypes, ObjectNode annotation) {
        static SubtypeSite of(JsonNode node) {
            if (!(node instanceof ObjectNode object) || object.size() != 1 || !(object.get("anyOf") instanceof ArrayNode anyOf) || anyOf.isEmpty()) {
                return null;
            }
            if (isBareRef(anyOf.get(0))) {
                for (JsonNode branch : anyOf) {
                    if (!isBareRef(branch)) {
                        return null;
                    }
                }
                return new SubtypeSite(anyOf, null);
            }

            if (!(anyOf.get(0).path("allOf").path(1) instanceof ObjectNode annotation) || annotation.has("anyOf") || annotation.has("$ref")) {
                return null;
            }
            ArrayNode subtypes = MAPPER.createArrayNode();
            for (JsonNode branch : anyOf) {
                if (branch.size() != 1 || !(branch.get("allOf") instanceof ArrayNode allOf) || allOf.size() != 2
                    || !isBareRef(allOf.get(0)) || !allOf.get(1).equals(annotation)) {
                    return null;
                }
                subtypes.add(allOf.get(0));
            }
            return new SubtypeSite(subtypes, annotation);
        }

        private static boolean isBareRef(JsonNode node) {
            return node.size() == 1 && node.path("$ref").isTextual();
        }
    }

    public static String withoutExamples(String markdown) {
        int heading = markdown.indexOf(JsonSchemaGenerator.EXAMPLES_HEADING);
        return heading < 0 ? markdown : markdown.substring(0, heading).stripTrailing();
    }

    private static void dropExamples(JsonNode schema) {
        if (!(schema instanceof ObjectNode object)) {
            return;
        }
        if (object.get("markdownDescription") instanceof TextNode description) {
            String trimmed = withoutExamples(description.asText());
            if (trimmed.isEmpty()) {
                object.remove("markdownDescription");
            } else {
                object.put("markdownDescription", trimmed);
            }
        }
        if (object.get("properties") instanceof ObjectNode properties) {
            properties.forEach(JsonSchemaCompactor::dropExamples);
        }
        forEachSubschema(object, JsonSchemaCompactor::dropExamples);
    }

    private static void walkProperties(JsonNode schema, BiPredicate<String, ObjectNode> shouldDescend) {
        if (!(schema instanceof ObjectNode object)) {
            return;
        }
        if (object.get("properties") instanceof ObjectNode properties) {
            properties.properties().forEach(entry ->
            {
                if (!(entry.getValue() instanceof ObjectNode property) || shouldDescend.test(entry.getKey(), property)) {
                    walkProperties(entry.getValue(), shouldDescend);
                }
            });
        }
        forEachSubschema(object, subschema -> walkProperties(subschema, shouldDescend));
    }

    // Follows schema keywords only: a literal under `default`, `const` or `enum` may hold keys that look like schema keywords.
    private static void forEachSubschema(ObjectNode schema, Consumer<JsonNode> action) {
        if (schema.get("definitions") instanceof ObjectNode definitions) {
            definitions.forEach(action);
        }
        for (String keyword : List.of("items", "additionalProperties", "not")) {
            if (schema.get(keyword) instanceof ObjectNode subschema) {
                action.accept(subschema);
            }
        }
        for (String keyword : List.of("anyOf", "allOf", "oneOf")) {
            if (schema.get(keyword) instanceof ArrayNode branches) {
                branches.forEach(action);
            }
        }
    }

    private static String uniqueKey(String base, Set<String> usedKeys) {
        String key = base;
        for (int suffix = 2; !usedKeys.add(key); suffix++) {
            key = base + "-" + suffix;
        }
        return key;
    }
}
