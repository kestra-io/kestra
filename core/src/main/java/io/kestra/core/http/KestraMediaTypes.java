package io.kestra.core.http;

import io.micronaut.http.MediaType;

/**
 * Media types Kestra handles beyond Micronaut's own constants.
 */
public final class KestraMediaTypes {

    /**
     * The YAML media type Kestra used before Micronaut 5.
     * <p>
     * Micronaut 5 adopted the IANA standard {@code application/yaml} (RFC 9512) as the value of
     * {@link MediaType#APPLICATION_YAML}, which until then was {@code application/x-yaml}. Both spellings must
     * still be recognised: it is what the Kestra UI, the generated SDK and existing API clients send, and what
     * plenty of third-party servers return.
     * <p>
     * Note that a check written as {@code MediaType.APPLICATION_YAML.equals(x) || "application/yaml".equals(x)}
     * silently stopped covering the legacy spelling on Micronaut 5, since both sides became the same value.
     */
    public static final String APPLICATION_X_YAML = "application/x-yaml";

    private KestraMediaTypes() {
    }
}
