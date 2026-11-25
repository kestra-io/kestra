package io.kestra.core.serializers;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.Label;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson 3 counterpart of {@link ListOrMapOfLabelDeserializer}, for the HTTP boundary.
 * <p>
 * Labels were first a map and became a list of Label, so both types must deserialize. It reads the tokens itself
 * for the same reason as the Jackson 2 version (see LabelsDeserializationBenchmark in jmh-benchmarks). Jackson 3 has no
 * separate {@code ResolvableDeserializer} interface: {@code resolve()} is a no-op default on
 * {@link ValueDeserializer}, so the empty override the Jackson 2 version carries is not needed here.
 */
public class Jackson3ListOrMapOfLabelDeserializer extends ValueDeserializer<List<Label>> {
    @Override
    public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) {
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

    private static Label readLabel(JsonParser p, DeserializationContext ctxt) {
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

    private static String readKey(JsonParser p) {
        if (p.currentToken().isStructStart()) {
            p.skipChildren();
            return null;
        }
        return p.getValueAsString();
    }

    private static Object readScalar(JsonParser p, DeserializationContext ctxt) {
        return switch (p.currentToken()) {
            case VALUE_STRING -> p.getString();
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
}
