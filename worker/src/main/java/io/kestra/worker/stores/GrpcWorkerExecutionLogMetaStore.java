package io.kestra.worker.stores;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.ErrorLogsRequest;
import io.kestra.controller.grpc.ExecutionLogsServiceGrpc;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.runners.DefaultExecutionLogMetaStore;
import io.kestra.core.runners.ExecutionLogMetaStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side implementation of {@link ExecutionLogMetaStore} that retrieves
 * execution logs metadata from the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses repositories (which are not available to workers).
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultExecutionLogMetaStore.class)
public class GrpcWorkerExecutionLogMetaStore implements ExecutionLogMetaStore {
    private static final TypeReference<BatchMessage<LogEntry>> LIST_TYPE = new TypeReference<>() {
    };

    private final ExecutionLogsServiceGrpc.ExecutionLogsServiceBlockingStub executionLogsServiceStub;
    private final WorkerInfo workerInfo;

    public GrpcWorkerExecutionLogMetaStore(ExecutionLogsServiceGrpc.ExecutionLogsServiceBlockingStub executionLogsServiceStub, WorkerInfo workerInfo) {
        this.executionLogsServiceStub = executionLogsServiceStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public List<LogEntry> errorLogs(String tenantId, String executionId) {
        ErrorLogsRequest errorLogsRequest = ErrorLogsRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setExecutionId(executionId)
            .build();
        OpaqueData response = executionLogsServiceStub.errorLogs(errorLogsRequest);
        return MessageFormats.JSON.fromByteString(response.getMessage(), LIST_TYPE).records();
    }
}
