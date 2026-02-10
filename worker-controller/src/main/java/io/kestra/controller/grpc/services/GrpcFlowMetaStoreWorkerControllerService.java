package io.kestra.controller.grpc.services;

import io.grpc.stub.StreamObserver;
import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC service implementation for worker meta store operations.
 * Provides namespace, tenant, and flow metadata to workers via gRPC.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class GrpcFlowMetaStoreWorkerControllerService extends WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceImplBase implements WorkerControllerService {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcFlowMetaStoreWorkerControllerService.class);

    private final FlowMetaStoreInterface flowMetaStore;

    @Inject
    public GrpcFlowMetaStoreWorkerControllerService(FlowMetaStoreInterface flowMetaStore) {
        this.flowMetaStore = flowMetaStore;
    }

    @Override
    public void isNamespaceExists(NamespaceRequest request, StreamObserver<BooleanResponse> responseObserver) {
        LOG.trace("Received isNamespaceExists request: tenantId={}, namespace={}", request.getTenantId(), request.getNamespace());

        try {
            boolean exists = flowMetaStore.isNamespaceExists(request.getTenantId(), request.getNamespace());

            BooleanResponse response = BooleanResponse.newBuilder()
                .setValue(exists)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error during isNamespaceExists", e);
            responseObserver.onError(e);
        }
    }
}