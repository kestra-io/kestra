package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import io.kestra.core.models.Label;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This deserializer is for historical purpose, labels was first a map but has been updated to a List of Label so
 * this deserializer allows using both types.
 */
public class ListOrMapOfLabelDeserializer extends JsonDeserializer<List<Label>> implements ResolvableDeserializer {
    @SuppressWarnings("unchecked")
    @Override
    public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        else if (p.hasToken(JsonToken.START_ARRAY)) {
            // deserialize as list
            List<Map<String, String>> ret = ctxt.readValue(p, List.class);
            return ret.stream().map(map -> new Label(map.get("key"), map.get("value"))).toList();
        }
        else if (p.hasToken(JsonToken.START_OBJECT)) {
            // deserialize as map
            Map<String, String> ret = ctxt.readValue(p, Map.class);
            return ret == null ? null : ret.entrySet().stream()
                .map(entry -> new Label(entry.getKey(), entry.getValue()))
                .toList();
        }
        throw new IllegalArgumentException("Unable to deserialize value as it's neither an object neither an array");
    }

    @Override
    public void resolve(DeserializationContext ctxt) throws JsonMappingException {}
}
