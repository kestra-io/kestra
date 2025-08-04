package io.kestra.controller.messages;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;

/**
 * Message for {@link io.kestra.controller.grpc.HeartbeatRequest}.
 */
public record HeartbeatMessage(
    ServiceInstance instance,
    Service.ServiceState newState,
    String reason
) {
}
