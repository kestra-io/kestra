package io.kestra.webserver.services.ai;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;

public interface AiConfiguration {
    String type();

    String modelName();

    default Double temperature() {
        return 0.7;
    }

    default Double topP() {
        return null;
    }

    /**
     * Custom HTTP headers to send with every request to the model API, {@code null} or empty when none are
     * configured. Lets a deployment authenticate against, or route through, an internal AI gateway.
     *
     * @return the headers to add to each request, keyed by header name.
     */
    @Nullable
    default Map<String, String> customHeaders() {
        return Map.of();
    }

    /**
     * This provider's spend ceiling as configured, or null when it declares none.
     *
     * <p>Nullable because this is the binding surface — a provider configuration is a record whose
     * {@code usageLimit} component implements this, and binding writes null when nothing was configured.
     * Callers read {@link AiServiceInterface#usageLimit()}, which expresses absence as an empty Optional.
     */
    @Nullable
    default AiUsageLimitConfiguration usageLimit() {
        return null;
    }
}
