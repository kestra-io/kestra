package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration properties for gRPC channel settings.
 * <p>
 * This configuration defines parameters for managing connections
 * to gRPC endpoints, including retry mechanisms and connection keep-alive behavior.
 *
 * @param maxRetryAttempts      Specifies the maximum number of retry attempts for a gRPC call.
 * @param keepAliveTime         Defines the duration for gRPC connection keep-alive.
 * @param shutdownTimeout       Defines the maximum time to wait for graceful channel shutdown.
 * @param maxInboundMessageSize Specifies the maximum size of inbound gRPC messages in bytes.
 */
@ConfigurationProperties("kestra.grpc.channel")
public record GrpcChannelConfiguration(
    @Bindable(defaultValue = "10")
    int maxRetryAttempts,
    @Bindable(defaultValue = "1h")
    Duration keepAliveTime,
    @Bindable(defaultValue = "30s")
    Duration shutdownTimeout,
    @Bindable(defaultValue = "10485760")  // 10 MB
    int maxInboundMessageSize
) {
}
