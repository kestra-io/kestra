package io.kestra.controller.grpc.services;

import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.worker.WorkerGroups;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service for handling worker connection and registration.
 * <p>
 * This service is called by workers when they start to resolve their group subscriptions
 * based on their worker group ID.
 */
@Singleton
@Slf4j
public class GrpcConnectControllerService extends ConnectControllerServiceGrpc.ConnectControllerServiceImplBase implements WorkerControllerService {

    protected final WorkerConfigsProvider workerConfigsProvider;

    @Inject
    public GrpcConnectControllerService(WorkerConfigsProvider workerConfigsProvider) {
        this.workerConfigsProvider = workerConfigsProvider;
    }

    @Override
    public void connect(ConnectRequest request, StreamObserver<ConnectResponse> responseObserver) {
        final String workerGroupId;
        try {
            workerGroupId = resolveWorkerGroupId(request);
        } catch (StatusRuntimeException e) {
            // A status escaping this method reaches the worker as UNKNOWN and is dumped as an ERROR
            // stack trace by the gRPC executor, so the rejection is delivered on the observer instead.
            log.warn(
                "Worker '{}' connect request rejected with {}: {}",
                request.getHeader().getClientId(),
                e.getStatus().getCode(),
                e.getStatus().getDescription()
            );
            responseObserver.onError(e);
            return;
        }
        log.info("Worker connect request received with workerGroup: {}", workerGroupId);

        ConnectResponse response = ConnectResponse.newBuilder()
            .setHeader(request.getHeader())
            .setWorkerGroupId(workerGroupId)
            .setWorkerConfigs(MessageFormats.JSON.toByteString(workerConfigsProvider.get()))
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Resolves the Worker Group id for the connecting worker. OSS always returns
     * {@link WorkerGroups#DEFAULT_ID}; the EE override resolves it from the authenticated
     * worker context and may reject the connection by throwing a {@link StatusRuntimeException},
     * whose status is then returned to the worker as-is.
     */
    protected String resolveWorkerGroupId(ConnectRequest request) {
        return WorkerGroups.DEFAULT_ID;
    }
}
