package io.kestra.core.serializers;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.Label;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson 3 counterpart of {@link ListOrMapOfLabelDeserializer}, for the HTTP boundary.
 * <p>
 * Labels were first a map and became a list of Label, so both types must deserialize. Jackson 3 has no
 * separate {@code ResolvableDeserializer} interface: {@code resolve()} is a no-op default on
 * {@link ValueDeserializer}, so the empty override the Jackson 2 version carries is not needed here.
 */
public class Jackson3ListOrMapOfLabelDeserializer extends ValueDeserializer<List<Label>> {
    @SuppressWarnings("unchecked")
    @Override
    public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        } else if (p.hasToken(JsonToken.START_ARRAY)) {
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
            Map<String, Object> ret = ctxt.readValue(p, Map.class);
            return ret == null ? null
                : ret.entrySet().stream()
                    .map(Jackson3ListOrMapOfLabelDeserializer::validateAndCreateLabel)
                    .toList();
        }
        throw new IllegalArgumentException("Unable to deserialize value as it's neither an object neither an array");
    }

    private static Label validateAndCreateLabel(Map.Entry<String, Object> entry) {
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
