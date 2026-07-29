package io.kestra.worker.stores;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.TaskOutputForTaskRequest;
import io.kestra.controller.grpc.TaskOutputProviderServiceGrpc;
import io.kestra.controller.grpc.TaskOutputRequest;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.runners.LazyOutputProvider;
import io.kestra.core.worker.models.WorkerInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * gRPC-backed {@link LazyOutputProvider} for distributed workers.
 * Fetches task outputs on-demand from the controller via gRPC.
 * <p>
 * Results for {@link #findTaskIdsWithOutput()} and {@link #valueToTaskIds()} are cached
 * since they are immutable for a given execution snapshot and called multiple times.
 */
@Slf4j
public class GrpcLazyOutputProvider implements LazyOutputProvider {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Set<String>> SET_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> VALUE_TO_TASK_IDS_TYPE = new TypeReference<>() {};

    private final TaskOutputProviderServiceGrpc.TaskOutputProviderServiceBlockingStub stub;
    private final WorkerInfo workerInfo;
    private final String tenantId;
    private final String executionId;

    // cached values (immutable for a given execution snapshot), guarded by synchronized
    private volatile Set<String> cachedTaskIdsWithOutput;
    private volatile Map<String, List<String>> cachedValueToTaskIds;

    public GrpcLazyOutputProvider(TaskOutputProviderServiceGrpc.TaskOutputProviderServiceBlockingStub stub,
                                   WorkerInfo workerInfo,
                                   String tenantId,
                                   String executionId) {
        this.stub = stub;
        this.workerInfo = workerInfo;
        this.tenantId = tenantId;
        this.executionId = executionId;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> computeOutputs() {
        log.trace("Fetching all outputs via gRPC: tenantId={}, executionId={}", tenantId, executionId);

        OpaqueData response = stub.computeOutputs(buildTaskOutputRequest());
        Map<String, Object> result = MESSAGE_FORMAT.fromByteString(response.getMessage(), MAP_TYPE);
        return result != null ? result : Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> computeOutputsForTask(String taskId) {
        log.trace("Fetching outputs for task via gRPC: tenantId={}, executionId={}, taskId={}", tenantId, executionId, taskId);

        TaskOutputForTaskRequest request = TaskOutputForTaskRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setExecutionId(executionId)
            .setTaskId(taskId)
            .build();

        OpaqueData response = stub.computeOutputsForTask(request);
        Map<String, Object> result = MESSAGE_FORMAT.fromByteString(response.getMessage(), MAP_TYPE);
        return result != null ? result : Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> findTaskIdsWithOutput() {
        if (cachedTaskIdsWithOutput != null) {
            return cachedTaskIdsWithOutput;
        }
        synchronized (this) {
            if (cachedTaskIdsWithOutput != null) {
                return cachedTaskIdsWithOutput;
            }
            log.trace("Fetching task IDs with output via gRPC: tenantId={}, executionId={}", tenantId, executionId);

            OpaqueData response = stub.findTaskIdsWithOutput(buildTaskOutputRequest());
            Set<String> result = MESSAGE_FORMAT.fromByteString(response.getMessage(), SET_TYPE);
            cachedTaskIdsWithOutput = result != null ? result : Collections.emptySet();
            return cachedTaskIdsWithOutput;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, List<String>> valueToTaskIds() {
        if (cachedValueToTaskIds != null) {
            return cachedValueToTaskIds;
        }
        synchronized (this) {
            if (cachedValueToTaskIds != null) {
                return cachedValueToTaskIds;
            }
            log.trace("Fetching valueToTaskIds via gRPC: tenantId={}, executionId={}", tenantId, executionId);

            OpaqueData response = stub.getValueToTaskIds(buildTaskOutputRequest());
            Map<String, List<String>> result = MESSAGE_FORMAT.fromByteString(response.getMessage(), VALUE_TO_TASK_IDS_TYPE);
            cachedValueToTaskIds = result != null ? result : Collections.emptyMap();
            return cachedValueToTaskIds;
        }
    }

    private TaskOutputRequest buildTaskOutputRequest() {
        return TaskOutputRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setExecutionId(executionId)
            .build();
    }
}
