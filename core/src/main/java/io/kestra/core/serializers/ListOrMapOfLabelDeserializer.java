package io.kestra.core.serializers;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.Label;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * This deserializer is for historical purpose, labels was first a map but has been updated to a List of Label so
 * this deserializer allows using both types.
 */
public class ListOrMapOfLabelDeserializer extends ValueDeserializer<List<Label>> {
    @SuppressWarnings("unchecked")
    @Override
    public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        } else if (p.hasToken(JsonToken.START_ARRAY)) {
            // deserialize as list
            List<Map<String, String>> ret = ctxt.readValue(p, List.class);
            return ret.stream().map(map ->
            {
                Object value = map.get("value");
                if (isAllowedType(value)) {
                    return new Label(map.get("key"), String.valueOf(value));
                } else {
                    throw new IllegalArgumentException("Unsupported type for key: " + map.get("key") + ", value: " + value);
                }
            }).toList();
        } else if (p.hasToken(JsonToken.START_OBJECT)) {
            // deserialize as map
            Map<String, Object> ret = ctxt.readValue(p, Map.class);
            return ret == null ? null
                : ret.entrySet().stream()
                    .map(this::validateAndCreateLabel)
                    .toList();
        }
        throw new IllegalArgumentException("Unable to deserialize value as it's neither an object neither an array");
    }

    private Label validateAndCreateLabel(Map.Entry<String, Object> entry) {
        Object value = entry.getValue();
        if (isAllowedType(value)) {
            return new Label(entry.getKey(), String.valueOf(value));
        } else {
            throw new IllegalArgumentException("Unsupported type for key: " + entry.getKey() + ", value: " + value);
        }
    }

    private static boolean isAllowedType(Object value) {
        return value instanceof String ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double ||
            value instanceof Boolean;
    }
}
