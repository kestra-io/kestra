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
     * This provider's spend ceiling, if it declares one.
     *
     * <p>A default rather than an abstract method so the ten existing provider configurations need no change:
     * a provider that says nothing about limits has its usage recorded and nothing shown or enforced.
     */
    default AiUsageLimitConfiguration usageLimit() {
        return AiUsageLimitConfiguration.DISABLED;
    }
}
