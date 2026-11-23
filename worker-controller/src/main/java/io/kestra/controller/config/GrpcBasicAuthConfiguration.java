package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration properties for HTTP Basic authentication of the worker-to-controller gRPC channel.
 * <p>
 * The same credentials are read on both sides: the controller expects them, the worker presents them.
 * They authenticate the fleet as a whole rather than an individual worker, so they carry no identity
 * and grant no worker-group membership.
 *
 * @param enabled Whether every gRPC call must carry the credentials below. Disabled by default.
 * @param username The user name presented and expected. Required when enabled.
 * @param password The password presented and expected. Required when enabled.
 */
@ConfigurationProperties(GrpcBasicAuthConfiguration.PREFIX)
public record GrpcBasicAuthConfiguration(
    @Bindable(defaultValue = "false") boolean enabled,
    @Nullable String username,
    @Nullable String password) {

    public static final String PREFIX = "kestra.grpc.basic-auth";

    public static final String ENABLED_PROPERTY = PREFIX + ".enabled";

    public GrpcBasicAuthConfiguration {
        if (enabled) {
            requireConfigured(username, "username");
            requireConfigured(password, "password");
        }
    }

    private static void requireConfigured(final String value, final String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Property %s.%s is required when gRPC basic authentication is enabled.".formatted(PREFIX, property)
            );
        }
    }
}
