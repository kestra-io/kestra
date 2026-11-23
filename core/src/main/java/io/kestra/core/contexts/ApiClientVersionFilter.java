package io.kestra.core.contexts;

import java.util.Objects;

import io.kestra.core.utils.VersionProvider;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

/**
 * Declares the instance's Kestra version on every call to Kestra's own API, so the API can answer
 * for the caller's version instead of its own.
 *
 * <p>Scoped to the {@code api} service id rather than to a URL pattern on purpose: the
 * {@code remote-api} and {@code proxy} services, and the unnamed client, all reach endpoints a user
 * configures, and the instance version must not leak to those.
 */
@ClientFilter(serviceId = "api")
public class ApiClientVersionFilter {
    public static final String VERSION_HEADER = "X-Kestra-Version";

    private final VersionProvider versionProvider;

    public ApiClientVersionFilter(VersionProvider versionProvider) {
        this.versionProvider = Objects.requireNonNull(versionProvider);
    }

    @RequestFilter
    public void declareVersion(MutableHttpRequest<?> request) {
        request.header(VERSION_HEADER, versionProvider.getVersion());
    }
}
