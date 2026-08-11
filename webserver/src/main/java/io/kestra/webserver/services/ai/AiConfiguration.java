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
}
