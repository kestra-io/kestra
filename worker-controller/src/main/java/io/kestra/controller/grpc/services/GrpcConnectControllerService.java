package io.kestra.controller.grpc.services;

import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.worker.WorkerGroups;

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
        String workerGroupId = resolveWorkerGroupId();
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
     * Resolves the Worker Group id. OSS always returns {@link WorkerGroups#DEFAULT_ID};
     * the EE override resolves it from the authenticated worker context.
     */
    protected String resolveWorkerGroupId() {
        return WorkerGroups.DEFAULT_ID;
    }
}
