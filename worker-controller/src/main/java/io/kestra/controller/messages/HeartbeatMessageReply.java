package io.kestra.controller.messages;


import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceStateTransition;

/**
 * Message for {@link io.kestra.controller.grpc.HeartbeatResponse}.
 */
public record HeartbeatMessageReply(
    ServiceInstance instance,
    ServiceStateTransition.Result result,
    boolean maintenance
) {
}
