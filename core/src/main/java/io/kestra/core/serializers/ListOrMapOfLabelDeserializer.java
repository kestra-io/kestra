package io.kestra.core.serializers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;

import io.kestra.core.models.Label;

/**
 * This deserializer is for historical purpose, labels was first a map but has been updated to a List of Label so
 * this deserializer allows using both types.
 * <p>
 * It reads the tokens itself rather than going through an intermediate {@code List<Map>}, since it runs on every
 * execution read: see LabelsDeserializationBenchmark in jmh-benchmarks.
 * <p>
 * Jackson 3 counterpart: {@link Jackson3ListOrMapOfLabelDeserializer}.
 */
public class ListOrMapOfLabelDeserializer extends JsonDeserializer<List<Label>> implements ResolvableDeserializer {
    @Override
    public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        } else if (p.hasToken(JsonToken.START_ARRAY)) {
            List<Label> labels = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                labels.add(readLabel(p, ctxt));
            }
            return labels;
        } else if (p.hasToken(JsonToken.START_OBJECT)) {
            List<Label> labels = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String key = p.currentName();
                p.nextToken();
                labels.add(label(key, readScalar(p, ctxt)));
            }
            return labels;
        }
        throw new IllegalArgumentException("Unable to deserialize value as it's neither an object neither an array");
    }

    private static Label readLabel(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.hasToken(JsonToken.START_OBJECT)) {
            throw new IllegalArgumentException("Unable to deserialize a label as it's not an object");
        }
        String key = null;
        Object value = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "key" -> key = readKey(p);
                case "value" -> value = readScalar(p, ctxt);
                default -> p.skipChildren();
            }
        }
        return label(key, value);
    }

    private static String readKey(JsonParser p) throws IOException {
        if (p.currentToken().isStructStart()) {
            p.skipChildren();
            return null;
        }
        return p.getValueAsString();
    }

    private static Object readScalar(JsonParser p, DeserializationContext ctxt) throws IOException {
        return switch (p.currentToken()) {
            case VALUE_STRING -> p.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> p.getNumberValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            // an object or an array is read so that the rejection below can show it
            default -> ctxt.readValue(p, Object.class);
        };
    }

    private static Label label(String key, Object value) {
        if (isAllowedType(value)) {
            return new Label(key, String.valueOf(value));
        }
        throw new IllegalArgumentException("Unsupported type for key: " + key + ", value: " + value);
    }

    private static boolean isAllowedType(Object value) {
        return value instanceof String ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double ||
            value instanceof Boolean;
    }

    @Override
    public void resolve(DeserializationContext ctxt) throws JsonMappingException {
    }
}
