package io.kestra.core.runners;

import java.util.List;
import java.util.Optional;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.repositories.CheckpointRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link CheckpointStateStore} that delegates directly to {@link CheckpointRepositoryInterface}.
 * <p>
 * This implementation is active on controller, webserver, and standalone servers where the repository is available.
 * Workers use a gRPC-based implementation instead.
 */
@Singleton
@Requires(beans = CheckpointRepositoryInterface.class)
public class DefaultCheckpointStateStore implements CheckpointStateStore {
    private final CheckpointRepositoryInterface checkpointRepository;

    @Inject
    public DefaultCheckpointStateStore(CheckpointRepositoryInterface checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    @Override
    public List<Checkpoint> list(String tenantId, String executionId, String taskRunId) {
        return checkpointRepository.findByTaskRun(tenantId, executionId, taskRunId);
    }

    @Override
    public Optional<Checkpoint> get(String tenantId, String executionId, String taskRunId, String key) {
        return checkpointRepository.findById(tenantId, executionId, taskRunId, key);
    }

    @Override
    public void set(Checkpoint checkpoint) {
        checkpointRepository.save(checkpoint);
    }
}
