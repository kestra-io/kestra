package io.kestra.controller.discovery;

import java.time.Instant;

/**
 * Payload written to internal storage by a controller so that workers using
 * {@code kestra.worker.controllers.type=STORAGE} can discover its gRPC endpoint.
 *
 * @param id          Unique identifier of the controller JVM (stable for the lifetime of the process).
 * @param host        The host advertised for gRPC connections.
 * @param port        The bound gRPC port.
 * @param heartbeatAt When the controller last refreshed this entry.
 * @param expiresAt   When this entry should be considered stale. Computed as
 *                    {@code heartbeatAt + heartbeatInterval * 3}.
 */
public record ControllerRegistration(
    String id,
    String host,
    int port,
    Instant heartbeatAt,
    Instant expiresAt) {

    /**
     * @return {@code true} if this entry has expired relative to the given instant.
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
