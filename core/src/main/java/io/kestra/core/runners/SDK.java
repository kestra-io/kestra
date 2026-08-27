package io.kestra.core.runners;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Provides convenient methods to interact with the Kestra API via the SDK.
 */
public interface SDK {
    /**
     * @return the default authentication to the API for SDK interactions, including the API URL to use if configured.
     *         On OSS: it returns an authentication configured inside the application configuration une the 'kestra.tasks.sdk.authentication' config properties.
     *         On EE: it tries first to locate a default authentication configured at the namespace level, then at the tenant level, then defaults to the application configuration provided one
     *         if any.
     */
    Optional<Auth> defaultAuthentication();

    /**
     * Same as {@link #defaultAuthentication()} but fails fast with an actionable error when no usable
     * credentials are configured (an API token, or a username and password — a URL alone is not enough).
     * <p>
     * SDK-based tasks should call this instead of silently building an unauthenticated client, which
     * surfaces to users as an opaque 401 Unauthorized at API-call time.
     *
     * @return the default authentication, guaranteed to carry an API token or a username/password pair.
     * @throws IllegalArgumentException when no default credentials are configured.
     */
    default Auth defaultAuthenticationOrThrow() {
        return defaultAuthentication()
            .filter(auth -> auth.apiToken().isPresent() || (auth.username().isPresent() && auth.password().isPresent()))
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "No authentication method provided for the Kestra API. Set the `auth` property on the task " +
                        "(apiToken or username/password), or configure default credentials via " +
                        "`kestra.tasks.sdk.authentication` (api-token, or username and password) in the Kestra configuration."
                )
            );
    }

    @ConfigurationProperties("kestra.tasks.sdk.authentication")
    record Auth(
        Optional<String> url,
        Optional<String> apiToken,
        Optional<String> username,
        Optional<String> password) {
    }
}
