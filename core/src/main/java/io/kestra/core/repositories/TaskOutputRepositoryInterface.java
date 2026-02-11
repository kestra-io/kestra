package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;

import java.util.List;
import java.util.Optional;

/**
 * Repository for task outputs, used to store and retrieve the outputs of tasks.
 * This is used by the {@link io.kestra.core.services.TaskOutputService} to store and retrieve the outputs of tasks.
 * WARNING: don't use it directly, use the {@link io.kestra.core.services.TaskOutputService}.
 */
public interface TaskOutputRepositoryInterface {
    /**
     * Find a task output by its id, which is a combination of tenantId and taskRunId.
     */
    Optional<TaskOutput> findById(String tenantId, String taskRunId);

    /**
     * Save a task output.
     */
    TaskOutput save(TaskOutput taskOutput);

    /**
     * Find all task outputs for a given execution.
     */
    List<TaskOutput> findByExecution(Execution execution);
}
