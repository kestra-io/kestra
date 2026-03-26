package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.controller.grpc.*;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.runners.ExecutionLogMetaStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service implementation for worker meta store operations.
 * Provides execution logs to workers via gRPC.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class GrpcExecutionLogController extends ExecutionLogsServiceGrpc.ExecutionLogsServiceImplBase implements WorkerControllerService {
    private final ExecutionLogMetaStore executionLogMetaStore;
    private final WorkerInfo workerInfo;

    public GrpcExecutionLogController(ExecutionLogMetaStore executionLogMetaStore, WorkerInfo workerInfo) {
        this.executionLogMetaStore = executionLogMetaStore;
        this.workerInfo = workerInfo;
    }

    @Override
    public void errorLogs(ErrorLogsRequest request, StreamObserver<OpaqueData> responseObserver) {
        log.trace("Received errorLogs request: tenantId={}, executionId={}", request.getTenantId(), request.getExecutionId());

        try {
            List<LogEntry> logEntries = executionLogMetaStore.errorLogs(request.getTenantId(), request.getExecutionId());

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MessageFormats.JSON.toByteString(BatchMessage.of(logEntries)))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during errorLogs", e);
            responseObserver.onError(e);
        }

    }
}
