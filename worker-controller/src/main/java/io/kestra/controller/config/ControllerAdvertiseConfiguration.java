package io.kestra.controller.config;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the controller self-advertisement in Kestra internal storage.
 * <p>
 * When enabled, the controller writes its reachable gRPC endpoint to a well-known location
 * in internal storage, so that workers configured with
 * {@code kestra.worker.controllers.type=STORAGE} can discover it dynamically.
 *
 * @param enabled           Whether the controller publishes its endpoint to internal storage.
 *                          Defaults to {@code false}; set to {@code true} to opt in to self-advertisement
 *                          when workers use {@code kestra.worker.controllers.type=STORAGE}.
 * @param host              The host that should be advertised to workers. When {@code null}, falls back
 *                          to {@link io.kestra.core.utils.Network#localHostname()}. Should be set
 *                          explicitly in containerized environments where the local hostname is not reachable.
 * @param heartbeatInterval How often the controller refreshes its registry entry. The published TTL
 *                          is {@code heartbeatInterval * 3} so that up to two missed schedules do not
 *                          cause workers to prematurely drop the entry. Defaults to 5 minutes.
 */
@ConfigurationProperties("kestra.controller.advertise")
public record ControllerAdvertiseConfiguration(
    @Bindable(defaultValue = "false") boolean enabled,
    @Nullable String host,
    @Bindable(defaultValue = "5m") Duration heartbeatInterval) {

    /**
     * Returns the TTL to publish in each registry entry. Set to three times the heartbeat interval,
     * so that up to two missed heartbeats do not immediately expire the entry.
     *
     * @return the registry entry TTL.
     */
    public Duration ttl() {
        return heartbeatInterval.multipliedBy(3);
    }
}
