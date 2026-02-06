package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration properties for the Kestra controller.
 *
 * @param port             The port number used by the controller. Defaults to 9096.
 * @param maxConnectionAge Maximum duration a worker connection can remain open before the server
 *                         triggers a graceful reconnection. This helps with load balancing across
 *                         multiple controllers by forcing workers to periodically reconnect and
 *                         potentially connect to a different controller. Defaults to 30 minutes.
 *                         Set to null or Duration.ZERO to disable.
 */
@ConfigurationProperties("kestra.controller")
public record ControllerConfiguration(
    @Bindable(defaultValue = DEFAULT_GRPC_PORT_STRING)
    int port,

    @Bindable(defaultValue = DEFAULT_MAX_CONNECTION_AGE_STRING)
    Duration maxConnectionAge
) {
    public static final int DEFAULT_GRPC_PORT = 9096;
    public static final String DEFAULT_GRPC_PORT_STRING = "9096";

    /**
     * Default max connection age: 30 minutes.
     * This provides a reasonable balance between connection stability and load redistribution.
     */
    public static final String DEFAULT_MAX_CONNECTION_AGE_STRING = "PT30M";
}
