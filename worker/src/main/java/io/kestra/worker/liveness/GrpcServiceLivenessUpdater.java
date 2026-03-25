package io.kestra.worker.liveness;

import java.util.Objects;
import java.util.UUID;

import io.kestra.controller.grpc.HeartbeatRequest;
import io.kestra.controller.grpc.HeartbeatResponse;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc.LivenessControllerServiceBlockingStub;
import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.messages.HeartbeatMessage;
import io.kestra.controller.messages.HeartbeatMessageReply;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessUpdater;
import io.kestra.core.server.ServiceStateTransition;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * An implementation of the ServiceLivenessUpdater interface that uses gRPC to update the liveness
 * state of service instances.
 * <p>
 * This implementation communicates with a remote LivenessControllerService using a blocking
 * gRPC stub to send heartbeat messages and local update service states.
 *
 * @see ServiceLivenessUpdater
 * @see LivenessControllerServiceBlockingStub
 * @see HeartbeatRequest
 * @see HeartbeatResponse
 */
@Singleton
@Primary
@Requires(property = "kestra.server-type", pattern = "(WORKER)")
public class GrpcServiceLivenessUpdater implements ServiceLivenessUpdater {

    private final LivenessControllerServiceBlockingStub client;

    /**
     * Creates a new GrpcServiceLivenessUpdater instance.
     *
     * @param client the gRPC client stub for communication with the LivenessControllerService
     */
    @Inject
    public GrpcServiceLivenessUpdater(final LivenessControllerServiceBlockingStub client) {
        this.client = Objects.requireNonNull(client, "client must not be null.");
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void update(ServiceInstance service) {
        update(service, null, null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceStateTransition.Response update(ServiceInstance instance, Service.ServiceState newState, String reason) {
        HeartbeatMessageReply messageReply = sendHeartbeatMessage(instance, newState, reason);

        return new ServiceStateTransition.Response(messageReply.result(), messageReply.instance());
    }

    protected HeartbeatMessageReply sendHeartbeatMessage(ServiceInstance instance, Service.ServiceState newState, String reason) {
        HeartbeatResponse response = client.heartbeat(
            HeartbeatRequest
                .newBuilder()
                .setHeader(
                    RequestOrResponseHeader
                        .newBuilder()
                        .setClientId(instance.uid())
                        .setClientVersion(KestraContext.getContext().getVersion())
                        .setMessageFormat(MessageFormats.JSON.name())
                        .setCorrelationId(UUID.randomUUID().toString())
                        .build()
                )
                .setMessage(MessageFormats.JSON.toByteString(new HeartbeatMessage(instance, newState, reason)))
                .build()
        );

        return MessageFormat
            .resolve(response.getHeader().getMessageFormat())
            .fromByteString(response.getMessage(), HeartbeatMessageReply.class);
    }
}
