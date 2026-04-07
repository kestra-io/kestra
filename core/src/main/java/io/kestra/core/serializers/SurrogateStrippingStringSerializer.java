package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Jackson serializer that replaces invalid UTF-16 surrogate sequences with
 * the Unicode replacement character U+FFFD before writing a string to JSON.
 *
 * PostgreSQL's jsonb type rejects JSON containing lone surrogates, which causes
 * the executor pod to crash in a retry loop (issue #14806).
 */
public class SurrogateStrippingStringSerializer extends StdSerializer<String> {

    private static final long serialVersionUID = 1L;

    public static final char REPLACEMENT_CHAR = '\uFFFD';

    public SurrogateStrippingStringSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(sanitize(value));
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Fast path: return original object if no surrogates present.
        boolean hasSurrogate = false;
        for (int i = 0, len = input.length(); i < len; i++) {
            if (Character.isSurrogate(input.charAt(i))) {
                hasSurrogate = true;
                break;
            }
        }
        if (!hasSurrogate) {
            return input;
        }

        // Slow path: rebuild string replacing invalid surrogate code units.
        int len = input.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(input.charAt(i + 1))) {
                    // Valid pair — keep both.
                    sb.append(c);
                    sb.append(input.charAt(++i));
                } else {
                    // Lone high surrogate — replace.
                    sb.append(REPLACEMENT_CHAR);
                }
            } else if (Character.isLowSurrogate(c)) {
                // Lone low surrogate — replace.
                sb.append(REPLACEMENT_CHAR);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}