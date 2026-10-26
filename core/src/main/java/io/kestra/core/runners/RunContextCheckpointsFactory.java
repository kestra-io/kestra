package io.kestra.core.runners;

import java.util.List;
import java.util.Optional;

import io.kestra.core.checkpoint.Checkpoint;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RunContextCheckpointsFactory {
    private final CheckpointStateStore checkpointStateStore;

    @Inject
    public RunContextCheckpointsFactory(CheckpointStateStore checkpointStateStore) {
        this.checkpointStateStore = checkpointStateStore;
    }

    public Checkpoints create(RunContext runContext) {
        return new CheckpointsImpl(runContext, checkpointStateStore);
    }

    static class CheckpointsImpl implements Checkpoints {
        private final RunContext runContext;
        private final CheckpointStateStore checkpointStateStore;

        CheckpointsImpl(RunContext runContext, CheckpointStateStore checkpointStateStore) {
            this.runContext = runContext;
            this.checkpointStateStore = checkpointStateStore;
        }

        @Override
        public List<Checkpoint> list() {
            return checkpointStateStore.list(runContext.flowInfo().tenantId(), runContext.taskRunInfo().executionId(), runContext.taskRunInfo().taskRunId());
        }

        @Override
        public Optional<String> get(String key) {
            return checkpointStateStore.get(runContext.flowInfo().tenantId(), runContext.taskRunInfo().executionId(), runContext.taskRunInfo().taskRunId(), key).map(it -> it.value());
        }

        @Override
        public void set(String key, String value) {
            var checkpoint = new Checkpoint(
                runContext.flowInfo().tenantId(),
                runContext.taskRunInfo().executionId(),
                runContext.taskRunInfo().taskRunId(),
                key,
                value
            );

            checkpointStateStore.set(checkpoint);
        }

    }
}
