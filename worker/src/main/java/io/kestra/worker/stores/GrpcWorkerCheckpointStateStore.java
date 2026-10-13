package io.kestra.worker.stores;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.CheckpointGetRequest;
import io.kestra.controller.grpc.CheckpointListRequest;
import io.kestra.controller.grpc.CheckpointServiceGrpc;
import io.kestra.controller.grpc.CheckpointSetRequest;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.runners.CheckpointStateStore;
import io.kestra.core.runners.DefaultCheckpointStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side implementation of {@link CheckpointStateStore} that communicates with the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses repositories (which are not available to workers).
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultCheckpointStateStore.class)
public class GrpcWorkerCheckpointStateStore implements CheckpointStateStore {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<List<Checkpoint>> LIST_TYPE = new TypeReference<>() {
    };

    private final CheckpointServiceGrpc.CheckpointServiceBlockingStub checkpointStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcWorkerCheckpointStateStore(final CheckpointServiceGrpc.CheckpointServiceBlockingStub checkpointStub,
        final WorkerInfo workerInfo) {
        this.checkpointStub = checkpointStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public List<Checkpoint> list(String tenantId, String executionId, String taskRunId) {
        log.trace("Listing checkpoints via gRPC: tenantId={}, executionId={}, taskRunId={}", tenantId, executionId, taskRunId);

        CheckpointListRequest request = CheckpointListRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setExecutionId(executionId)
            .setTaskRunId(taskRunId)
            .build();

        OpaqueData response = checkpointStub.list(request);

        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    @Override
    public Optional<Checkpoint> get(String tenantId, String executionId, String taskRunId, String key) {
        log.trace("Fetching checkpoint via gRPC: tenantId={}, executionId={}, taskRunId={}, key={}", tenantId, executionId, taskRunId, key);

        CheckpointGetRequest request = CheckpointGetRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setExecutionId(executionId)
            .setTaskRunId(taskRunId)
            .setKey(key)
            .build();

        OpaqueData response = checkpointStub.get(request);

        return Optional.ofNullable(MESSAGE_FORMAT.fromByteString(response.getMessage(), Checkpoint.class));
    }

    @Override
    public void set(Checkpoint checkpoint) {
        log.trace("Setting checkpoint via gRPC: executionId={}, taskRunId={}, key={}", checkpoint.executionId(), checkpoint.taskRunId(), checkpoint.key());

        CheckpointSetRequest request = CheckpointSetRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(checkpoint.tenantId())
            .setExecutionId(checkpoint.executionId())
            .setTaskRunId(checkpoint.taskRunId())
            .setKey(checkpoint.key())
            .setValue(checkpoint.value())
            .build();

        checkpointStub.set(request);
    }
}
