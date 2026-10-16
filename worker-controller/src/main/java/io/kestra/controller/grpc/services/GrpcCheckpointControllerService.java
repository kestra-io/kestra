package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.controller.RequiresControllerServer;
import io.kestra.controller.grpc.CheckpointGetRequest;
import io.kestra.controller.grpc.CheckpointListRequest;
import io.kestra.controller.grpc.CheckpointServiceGrpc;
import io.kestra.controller.grpc.CheckpointSetRequest;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.runners.CheckpointStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * gRPC service implementation for task run checkpoint operations.
 * Provides worker-safe checkpoint read/write to workers via gRPC.
 */
@Singleton
@RequiresControllerServer
public class GrpcCheckpointControllerService extends CheckpointServiceGrpc.CheckpointServiceImplBase implements WorkerControllerService {

    private static final Logger log = LoggerFactory.getLogger(GrpcCheckpointControllerService.class);
    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;

    private final CheckpointStateStore checkpointStateStore;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcCheckpointControllerService(final CheckpointStateStore checkpointStateStore, final WorkerInfo workerInfo) {
        this.checkpointStateStore = checkpointStateStore;
        this.workerInfo = workerInfo;
    }

    @Override
    public void list(CheckpointListRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received list request: tenantId={}, executionId={}, taskRunId={}",
                request.getTenantId(), request.getExecutionId(), request.getTaskRunId()
            );

            List<Checkpoint> result = checkpointStateStore.list(request.getTenantId(), request.getExecutionId(), request.getTaskRunId());

            respond(responseObserver, result);
        } catch (Exception e) {
            fail("list", e, responseObserver);
        }
    }

    @Override
    public void get(CheckpointGetRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received get request: tenantId={}, executionId={}, taskRunId={}, key={}",
                request.getTenantId(), request.getExecutionId(), request.getTaskRunId(), request.getKey()
            );

            Optional<Checkpoint> result = checkpointStateStore.get(
                request.getTenantId(), request.getExecutionId(), request.getTaskRunId(), request.getKey()
            );

            respond(responseObserver, result.orElse(null));
        } catch (Exception e) {
            fail("get", e, responseObserver);
        }
    }

    @Override
    public void set(CheckpointSetRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received set request: tenantId={}, executionId={}, taskRunId={}, key={}",
                request.getTenantId(), request.getExecutionId(), request.getTaskRunId(), request.getKey()
            );

            // the tenant always comes from the request field so a worker cannot write on behalf of another tenant
            checkpointStateStore.set(
                new Checkpoint(request.getTenantId(), request.getExecutionId(), request.getTaskRunId(), request.getKey(), request.getValue())
            );

            respond(responseObserver, null);
        } catch (Exception e) {
            fail("set", e, responseObserver);
        }
    }

    private void respond(StreamObserver<OpaqueData> responseObserver, Object result) {
        OpaqueData response = OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setMessage(MESSAGE_FORMAT.toByteString(result))
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static void fail(String operation, Exception e, StreamObserver<OpaqueData> responseObserver) {
        log.error("Error during {}", operation, e);
        responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
    }
}
