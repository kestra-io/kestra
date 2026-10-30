package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.executions.ExecutionOutput;

/**
 * Repository for execution outputs, used to store and retrieve the flow-level outputs of an execution.
 * This is used by the {@link io.kestra.core.services.ExecutionOutputService} to store and retrieve the outputs of executions.
 * WARNING: don't use it directly, use the {@link io.kestra.core.services.ExecutionOutputService}.
 */
public interface ExecutionOutputRepositoryInterface {
    /**
     * Find an execution output by its id, which is a combination of tenantId and executionId.
     */
    Optional<ExecutionOutput> findById(String tenantId, String executionId);

    /**
     * Save an execution output.
     */
    ExecutionOutput save(ExecutionOutput executionOutput);

    /**
     * Purge (hard delete) all execution outputs for a given list of execution ids.
     *
     * @return the number of deleted outputs
     */
    int purgeByExecutionIds(List<String> executionIds);
}
