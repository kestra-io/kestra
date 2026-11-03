package io.kestra.core.runners;

import java.util.List;
import java.util.Optional;

import io.kestra.core.checkpoint.Checkpoint;

/**
 * Abstraction layer for task run checkpoint operations, used by {@link Checkpoints}.
 * <p>
 * On controller/webserver/standalone servers, the default implementation delegates directly to
 * {@link io.kestra.core.repositories.CheckpointRepositoryInterface}.
 * On workers, a gRPC-based implementation communicates with the controller.
 * <p>
 * This interface only exposes worker-safe operations. Purge is server-only and requires direct access to
 * {@link io.kestra.core.repositories.CheckpointRepositoryInterface}.
 */
public interface CheckpointStateStore {
    List<Checkpoint> list(String tenantId, String executionId, String taskRunId);

    Optional<Checkpoint> get(String tenantId, String executionId, String taskRunId, String key);

    /**
     * Save a checkpoint, replacing the value of an existing checkpoint with the same tenant, execution, task run and key.
     */
    void set(Checkpoint checkpoint);
}
