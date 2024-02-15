package io.kestra.core.server;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

/**
 * Server configuration.
 *
 * @param terminationGracePeriod The expected time a worker to complete all of its
 *                               tasks before initiating a graceful shutdown.
 */
@ConfigurationProperties("kestra.server")
public record ServerConfig(
    @NotNull
    @Bindable(defaultValue = "5m")
    Duration terminationGracePeriod,

    Liveness liveness

) {
    /**
     * Configuration for Liveness and Heartbeat mechanism between Kestra Services, and Executor.
     *
     * @param interval          The expected time between liveness probe.
     * @param timeout           The timeout used to detect service failures.
     *                          Kestra services sends periodic heartbeats to indicate their liveness.
     *                          For Workers, if no heartbeats are received by the executor before the expiration of this session timeout,
     *                          then the executor will remove any timeout workers from the cluster and eventually resubmit all their tasks.
     * @param initialDelay      The time to wait before executing a liveness probe for a service.
     * @param heartbeatInterval The expected time between heartbeats.
     */
    @ConfigurationProperties("liveness")
    public record Liveness(
        @NotNull
        @Bindable(defaultValue = "true") Boolean enabled,
        @NotNull @Bindable(defaultValue = "5s")
        Duration interval,
        @NotNull @Bindable(defaultValue = "45s") Duration timeout,
        @NotNull @Bindable(defaultValue = "30s") Duration initialDelay,
        @NotNull @Bindable(defaultValue = "3s") Duration heartbeatInterval
    ) {
    }
}
