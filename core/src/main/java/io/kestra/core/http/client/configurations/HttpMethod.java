package io.kestra.core.http.client.configurations;

import java.util.Locale;

import jakarta.annotation.Nullable;

/**
 * The HTTP methods that can be used to scope client behavior (e.g. retry rules) per request method.
 */
public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    PATCH,
    OPTIONS,
    TRACE;

    /**
     * Case-insensitive lookup from the raw method string carried by a request.
     *
     * @return the matching method, or {@code null} for a null or non-standard verb, so callers can fall
     *         back to their default behavior instead of failing.
     */
    @Nullable
    public static HttpMethod fromString(@Nullable String method) {
        if (method == null) {
            return null;
        }

        try {
            return valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}