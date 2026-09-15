package io.kestra.executor.testkit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;

/**
 * Map-backed {@link ExecutionOutputRepositoryInterface} so the real
 * {@link io.kestra.core.services.ExecutionOutputService} can run without a database.
 */
public class InMemoryExecutionOutputRepository implements ExecutionOutputRepositoryInterface {
    private final Map<String, ExecutionOutput> outputs = new ConcurrentHashMap<>();

    @Override
    public Optional<ExecutionOutput> findById(String tenantId, String executionId) {
        return Optional.ofNullable(outputs.get(key(tenantId, executionId)));
    }

    @Override
    public ExecutionOutput save(ExecutionOutput executionOutput) {
        outputs.put(key(executionOutput.tenantId(), executionOutput.executionId()), executionOutput);
        return executionOutput;
    }

    @Override
    public int purgeByExecutionIds(List<String> executionIds) {
        int before = outputs.size();
        outputs.values().removeIf(output -> executionIds.contains(output.executionId()));
        return before - outputs.size();
    }

    private static String key(String tenantId, String executionId) {
        return tenantId + "|" + executionId;
    }
}
