package io.kestra.core.runners;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Optional;

/**
 * Provides convenient methods to interact with the Kestra API via the SDK.
 */
public interface SDK {
    /**
     * @return the default authentication to the API for SDK interactions.
     * On OSS: it returns an authentication configured inside the application configuration une the 'kestra.tasks.sdk.authentication' config properties.
     * On EE: it tries first to locate a default authentication configured at the namespace level, then at the tenant level, then defaults to the application configuration provided one if any.
     */
    Optional<Auth> defaultAuthentication();

    @ConfigurationProperties("kestra.tasks.sdk.authentication")
    record Auth (
        Optional<String> apiToken,
        Optional<String> username,
        Optional<String> password
    ) {}
}
