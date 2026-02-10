package io.kestra.controller.grpc.services;

import io.grpc.stub.StreamObserver;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.services.WorkerGroupService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service for handling worker connection and registration.
 * <p>
 * This service is called by workers when they start to resolve their worker group
 * based on the configured worker group key.
 */
@Singleton
@Slf4j
public class GrpcConnectControllerService extends ConnectControllerServiceGrpc.ConnectControllerServiceImplBase implements WorkerControllerService {

    public static final String DEFAULT_WORKER_GROUP = "";
    private final WorkerGroupService workerGroupService;

    @Inject
    public GrpcConnectControllerService(WorkerGroupService workerGroupService) {
        this.workerGroupService = workerGroupService;
    }

    /**
     * Handles worker connection requests.
     * <p>
     * Resolves the worker group based on the provided worker group key using the
     * {@link WorkerGroupService}.
     *
     * @param request          the connect request containing the worker group key
     * @param responseObserver the response observer to send the resolved worker group
     */
    @Override
    public void connect(ConnectRequest request, StreamObserver<ConnectResponse> responseObserver) {
        String workerGroupKey = request.getWorkerGroupKey();
        log.info("Worker connect request received with workerGroupKey: {}", workerGroupKey);

        String resolvedWorkerGroup = workerGroupService.resolveGroupFromKey(workerGroupKey);

        if (resolvedWorkerGroup != null && !resolvedWorkerGroup.isEmpty()) {
            log.debug("Worker group resolved: '{}' for key '{}'", resolvedWorkerGroup, workerGroupKey);
        } else {
            log.debug("No worker group resolved for key '{}'", workerGroupKey);
        }

        ConnectResponse response = ConnectResponse.newBuilder()
            .setHeader(request.getHeader())
            .setWorkerGroup(resolvedWorkerGroup != null ? resolvedWorkerGroup : DEFAULT_WORKER_GROUP)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}