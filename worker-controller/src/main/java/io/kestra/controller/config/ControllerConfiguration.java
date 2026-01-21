package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration properties for the Kestra controller.
 *
 * @param port The port number used by the controller. Defaults to 9096.
 */
@ConfigurationProperties("kestra.controller")
public record ControllerConfiguration(
    @Bindable(defaultValue = DEFAULT_GRPC_PORT_STRING)
    int port
) {
    public static final int DEFAULT_GRPC_PORT = 9096;
    public static final String DEFAULT_GRPC_PORT_STRING = "9096";
}
