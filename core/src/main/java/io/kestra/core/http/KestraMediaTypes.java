package io.kestra.core.http;

/**
 * Media types Kestra handles beyond Micronaut's own constants.
 */
public final class KestraMediaTypes {

    /**
     * The non-standard YAML media type {@code application/x-yaml};
     * which is now superseded by IANA standard {@code application/yaml} (RFC 9512)
     */
    public static final String APPLICATION_X_YAML = "application/x-yaml";

    private KestraMediaTypes() {
    }
}
