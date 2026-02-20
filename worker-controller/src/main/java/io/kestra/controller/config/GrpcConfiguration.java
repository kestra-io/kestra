package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration properties for global gRPC settings.
 *
 * @param reflectionEnabled    Specifies whether gRPC reflection is enabled for the controller service. 
 *                             Enabling reflection allows clients to query the server for available services and methods,
 *                             which can be useful for debugging and development tools. Defaults to false.
 */
@ConfigurationProperties("kestra.grpc")
public record GrpcConfiguration(
    @Bindable(defaultValue = "false")
    boolean reflectionEnabled
) {
}
