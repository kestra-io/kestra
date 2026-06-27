package io.kestra.core.secret;

import java.util.Map;
import java.util.Objects;

/**
 * A secret in full mode: the main value plus any additional fields.
 *
 * @param value the main secret value.
 * @param metadata the additional fields, empty when there are none. Never null.
 */
public record SecretObject(String value, Map<String, String> metadata) {
    public SecretObject {
        Objects.requireNonNull(metadata, "metadata cannot be null");
    }

    public SecretObject(String value) {
        this(value, Map.of());
    }
}
