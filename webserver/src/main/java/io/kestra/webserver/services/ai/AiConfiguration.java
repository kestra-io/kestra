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
     * <p>Nullable rather than optional because this is the binding surface: every provider configuration is a
     * record whose {@code usageLimit} component implements this, and Jackson writes a null there when an
     * operator wrote nothing. Absence is expressed as an empty {@link java.util.Optional} one level up, at
     * {@link AiServiceInterface#usageLimit()}, which is what the rest of the code reads.
     *
     * <p>A default rather than an abstract method so the ten existing provider configurations need no change:
     * a provider that says nothing about limits has its usage recorded and nothing shown or enforced.
     */
    @Nullable
    default AiUsageLimitConfiguration usageLimit() {
        return null;
    }
}
