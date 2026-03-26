package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration properties for global gRPC settings.
 *
 * @param reflectionEnabled Specifies whether gRPC reflection is enabled for the controller service.
 *        Enabling reflection allows clients to query the server for available services and methods,
 *        which can be useful for debugging and development tools. Defaults to false.
 * @param maxInboundMessageSize Maximum inbound message size in bytes, applied to both the gRPC server and client channel.
 *        Defaults to {@link Integer#MAX_VALUE} (no limit).
 */
@ConfigurationProperties("kestra.grpc")
public record GrpcConfiguration(
    @Bindable(defaultValue = "false") boolean reflectionEnabled,
    @Bindable(defaultValue = "10485760") // 10 MB
    int maxInboundMessageSize) {
}
