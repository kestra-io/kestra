package io.kestra.core.serializers;

import java.util.List;
import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 counterpart of {@link ListOrMapOfLabelSerializer}, for the HTTP boundary.
 * <p>
 * Labels were first a map and became a list of Label, so both types must serialize.
 */
public class Jackson3ListOrMapOfLabelSerializer extends ValueSerializer<Object> {
    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext context) {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof List) {
            context.findValueSerializer(List.class).serialize(value, gen, context);
        } else if (value instanceof Map) {
            context.findValueSerializer(Map.class).serialize(value, gen, context);
        } else {
            throw new IllegalArgumentException("Unable to serialize value as it's neither a map nor a list");
        }
    }
}
