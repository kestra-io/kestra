package io.kestra.core.secret;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encoding of the {@code SECRET_*} environment variables read by the environment-based secret backend.
 */
public enum SecretEncoding {
    /**
     * The value is Base64-encoded, line breaks are ignored. Default, and the only historical behavior.
     */
    BASE64 {
        @Override
        public String decode(final String value) {
            return new String(Base64.getDecoder().decode(value.replaceAll("\\R", "")), StandardCharsets.UTF_8);
        }
    },

    /**
     * The value is used as-is, which is what a Kubernetes Secret injected as an environment variable delivers.
     */
    RAW {
        @Override
        public String decode(final String value) {
            return value;
        }
    };

    /**
     * Decodes an environment variable value into the secret it carries.
     *
     * @param value the raw environment variable value
     * @return the secret value
     * @throws IllegalArgumentException if the value does not match the encoding
     */
    public abstract String decode(String value);
}
