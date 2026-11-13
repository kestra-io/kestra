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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.serializers.JacksonMapper;

/**
 * Shrinks a generated Draft-7 schema by replacing repeated subtrees with a {@code $ref} to one shared definition.
 * The result validates identically, but only suits consumers that resolve {@code $ref}, such as the YAML language service.
 */
public final class JsonSchemaCompactor {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFINITIONS_REF = "#/definitions/";
    private static final String SHARED_KEY_PREFIX = "shared-property-";
    // Below this serialized size, the $ref and its definition key cost about as much as the copy they replace.
    private static final int MIN_SHARED_LENGTH = 128;

    private JsonSchemaCompactor() {
    }

    /** Returns a compacted copy of {@code schema}: subtype lists hoisted, then repeated property schemas shared. */
    public static Map<String, Object> compact(Map<String, Object> schema) {
        ObjectNode root = MAPPER.convertValue(schema, ObjectNode.class);
        hoistSubtypeLists(root);
        sharePropertySchemas(root);
        return MAPPER.convertValue(root, MAP_TYPE);
    }

    /**
     * Moves every property schema that occurs more than once, such as the ones each task inherits, to one {@code shared-property-<name>} definition.
     * Safe to run again on a schema that gained definitions since: property schemas that are already a {@code $ref} are left alone.
     */
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

        Map<JsonNode, String> refBySubtypes = new HashMap<>();
        Set<JsonNode> hoisted = Collections.newSetFromMap(new IdentityHashMap<>());
        definitions.properties().forEach(entry ->
        {
            if (isBareSubtypeList(entry.getValue()) && refBySubtypes.putIfAbsent(entry.getValue().get("anyOf"), DEFINITIONS_REF + entry.getKey()) == null) {
                hoisted.add(entry.getValue());
            }
        });
        if (!refBySubtypes.isEmpty()) {
            replaceSubtypeLists(schema, refBySubtypes, hoisted);
        }
    }

    private static void replaceSubtypeLists(JsonNode node, Map<JsonNode, String> refBySubtypes, Set<JsonNode> hoisted) {
        if (hoisted.contains(node)) {
            return;
        }
        if (isBareSubtypeList(node) && refBySubtypes.get(node.get("anyOf")) instanceof String ref) {
            ((ObjectNode) node).removeAll().put("$ref", ref);
            return;
        }
        node.forEach(child -> replaceSubtypeLists(child, refBySubtypes, hoisted));
    }

    private static boolean isBareSubtypeList(JsonNode node) {
        if (!(node instanceof ObjectNode object) || object.size() != 1 || !(object.get("anyOf") instanceof ArrayNode anyOf) || anyOf.isEmpty()) {
            return false;
        }
        for (JsonNode branch : anyOf) {
            if (branch.size() != 1 || !branch.path("$ref").isTextual()) {
                return false;
            }
        }
        return true;
    }

    // Follows schema keywords only: a literal under `default`, `const` or `enum` may hold a `properties` key that is not a schema.
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
        if (object.get("definitions") instanceof ObjectNode definitions) {
            definitions.forEach(definition -> walkProperties(definition, shouldDescend));
        }
        for (String keyword : List.of("items", "additionalProperties", "not")) {
            walkProperties(object.get(keyword), shouldDescend);
        }
        for (String keyword : List.of("anyOf", "allOf", "oneOf")) {
            if (object.get(keyword) instanceof ArrayNode branches) {
                branches.forEach(branch -> walkProperties(branch, shouldDescend));
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
