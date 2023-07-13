package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This serializer is for historical purpose, labels was first a map but has been updated to a List of Label so
 * this serializer allows using both types.
 */
public class ListOrMapOfLabelSerializer extends JsonSerializer<Object> {
    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        }
        else if (value instanceof List) {
            serializers.findValueSerializer(List.class).serialize(value, gen, serializers);
        }
        else if (value instanceof Map) {
            serializers.findValueSerializer(Map.class).serialize(value, gen, serializers);
        }
        else {
            throw new IllegalArgumentException("Unable to serialize value as it's neither a map nor a list");
        }
    }
}
