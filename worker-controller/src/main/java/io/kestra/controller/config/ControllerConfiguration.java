package io.kestra.controller.config;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration properties for the Kestra controller.
 *
 * @param port The port number used by the controller. Defaults to 50051.
 * @param maxConnectionAge Maximum duration a worker connection can remain open before the server
 *        triggers a graceful reconnection. This helps with load balancing across
 *        multiple controllers by forcing workers to periodically reconnect and
 *        potentially connect to a different controller. Defaults to 1 hour.
 *        Set to null or Duration.ZERO to disable.
 */
@ConfigurationProperties("kestra.controller")
public record ControllerConfiguration(
    @Bindable(defaultValue = DEFAULT_GRPC_PORT_STRING) int port,
    @Bindable(defaultValue = DEFAULT_MAX_CONNECTION_AGE_STRING) Duration maxConnectionAge,
    @Bindable(defaultValue = DEFAULT_MAX_CONNECTION_AGE_GRACE_STRING) Duration maxConnectionAgeGrace) {
    public static final int DEFAULT_GRPC_PORT = 50051;
    public static final String DEFAULT_GRPC_PORT_STRING = "50051";

    /**
     * Default max connection age: 1 hour.
     * This provides a reasonable balance between connection stability and load redistribution.
     */
    public static final String DEFAULT_MAX_CONNECTION_AGE_STRING = "PT1H";
    /**
     * Default max connection age grace period: 30 seconds.
     */
    public static final String DEFAULT_MAX_CONNECTION_AGE_GRACE_STRING = "PT30S";
}
