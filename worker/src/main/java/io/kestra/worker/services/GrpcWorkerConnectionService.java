package io.kestra.worker.services;

import io.kestra.controller.grpc.ConnectControllerServiceGrpc.ConnectControllerServiceBlockingStub;
import io.kestra.controller.grpc.ConnectRequest;
import io.kestra.controller.grpc.ConnectResponse;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC-based implementation of {@link WorkerConnectionService}.
 * <p>
 * This service communicates with the controller via gRPC to establish the initial
 * worker connection and resolve configuration such as worker group assignment.
 */
@Singleton
@Slf4j
public class GrpcWorkerConnectionService implements WorkerConnectionService {

    private final ConnectControllerServiceBlockingStub connectControllerService;

    @Inject
    public GrpcWorkerConnectionService(ConnectControllerServiceBlockingStub connectControllerService) {
        this.connectControllerService = connectControllerService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionResult connect(String workerId, String workerGroupKey) {
        log.debug("Connecting to controller to resolve worker group for key: {}", workerGroupKey);

        ConnectRequest request = ConnectRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerId))
            .setWorkerGroupKey(workerGroupKey != null ? workerGroupKey : "")
            .build();

        try {
            log.debug("Sending connect request to controller for workerId: {}, workerGroupKey: {}", workerId, workerGroupKey);
            ConnectResponse response = connectControllerService.connect(request);
            String resolvedGroup = response.getWorkerGroup();
            if (resolvedGroup == null || resolvedGroup.isEmpty()) {
                log.debug("No worker group resolved for key: {}", workerGroupKey);
                return new ConnectionResult(null);
            }

            log.info("Worker group resolved via connect service: '{}' for key '{}'", resolvedGroup, workerGroupKey);
            return new ConnectionResult(resolvedGroup);
        } catch (Exception e) {
            log.error("Failed to send connect request to controller", e);
            throw new WorkerConnectionFailedException("Failed connecting to Kestra controller. Cause: " + e.getMessage());
        }
    }
}