package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.models.executions.Execution;

/**
 * Repository for task run checkpoints.
 * WARNING: never call it from the worker, a facade must be used instead.
 */
public interface CheckpointRepositoryInterface {
    Optional<Checkpoint> findById(String tenantId, String executionId, String taskRunId, String key);

    List<Checkpoint> findByTaskRun(String tenantId, String executionId, String taskRunId);

    /**
     * Save a checkpoint, replacing the value of an existing checkpoint with the same tenant, execution, task run and key.
     */
    Checkpoint save(Checkpoint checkpoint);

    /**
     * Purge (hard delete) all checkpoints of the given executions.
     *
     * @return the number of deleted checkpoints
     */
    int purge(List<Execution> executions);
}
