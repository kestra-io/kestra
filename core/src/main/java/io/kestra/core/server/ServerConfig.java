package io.kestra.core.server;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotNull;

/**
 * Server configuration.
 *
 * @param terminationGracePeriod The expected time a worker to complete all of its
 *        tasks before initiating a graceful shutdown.
 */
@ConfigurationProperties("kestra.server")
public record ServerConfig(
    @NotNull
    @Bindable(defaultValue = "5m") Duration terminationGracePeriod,

    @Bindable(defaultValue = "AFTER_TERMINATION_GRACE_PERIOD")
    @Nullable WorkerTaskRestartStrategy workerTaskRestartStrategy,

    Liveness liveness,

    @Nullable Preview preview,

    @Nullable Standalone standalone,

    @Nullable Service service

) {

    public WorkerTaskRestartStrategy workerTaskRestartStrategy() {
        return Optional
            .ofNullable(workerTaskRestartStrategy)
            .orElse(WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD);
    }

    /**
     * Configuration for Liveness and Heartbeat mechanism between Kestra Services, and Executor.
     *
     * @param interval The expected time between liveness probe.
     * @param timeout The timeout used to detect service failures.
     *        Kestra services sends periodic heartbeats to indicate their liveness.
     *        For Workers, if no heartbeats are received by the executor before the expiration of this session timeout,
     *        then the executor will remove any timeout workers from the cluster and eventually resubmit all their tasks.
     * @param initialDelay The time to wait before executing a liveness probe for a service.
     * @param heartbeatInterval The expected time between heartbeats.
     */
    @ConfigurationProperties("liveness")
    public record Liveness(
        @NotNull
        @Bindable(defaultValue = "true") Boolean enabled,
        @NotNull @Bindable(defaultValue = "5s") Duration interval,
        @NotNull @Bindable(defaultValue = "45s") Duration timeout,
        @NotNull @Bindable(defaultValue = "45s") Duration initialDelay,
        @NotNull @Bindable(defaultValue = "3s") Duration heartbeatInterval) {
    }

    @ConfigurationProperties("preview")
    public record Preview(
        @Bindable(defaultValue = "100") Integer initialRows,
        @Bindable(defaultValue = "5000") Integer maxRows) {
    }

    @ConfigurationProperties("standalone")
    public record Standalone(
        @Nullable Running running) {

        @ConfigurationProperties("running")
        public record Running(
            @Bindable(defaultValue = "PT1M") Duration timeout) {
        }
    }

    @ConfigurationProperties("service")
    public record Service(
        @Nullable Purge purge) {

        @ConfigurationProperties("purge")
        public record Purge(
            @Nullable Duration retention) {
        }
    }
}
