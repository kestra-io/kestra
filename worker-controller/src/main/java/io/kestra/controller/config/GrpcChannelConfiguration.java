package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

@ConfigurationProperties("kestra.worker.grpc.channel")
public record GrpcChannelConfiguration(
    @Bindable(defaultValue = "localhost")
    String host,
    @Bindable(defaultValue = "9096")
    int port,
    @Bindable(defaultValue = "10")
    int maxRetryAttempts,
    @Bindable(defaultValue = "1h")
    Duration keepAliveTime
) {
}
