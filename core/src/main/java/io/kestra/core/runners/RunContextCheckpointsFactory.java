package io.kestra.core.runners;

import io.kestra.core.checkpoint.Checkpoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

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
        private final String tenantId;
        private final RunContext.TaskRunInfo taskRunInfo;
        private final CheckpointStateStore checkpointStateStore;

        CheckpointsImpl(RunContext runContext, CheckpointStateStore checkpointStateStore) {
            this.tenantId = runContext.flowInfo().tenantId();
            this.taskRunInfo = runContext.taskRunInfo();
            this.checkpointStateStore = checkpointStateStore;
        }

        @Override
        public List<Checkpoint> list() {
            return checkpointStateStore.list(tenantId, taskRunInfo.executionId(), taskRunInfo.taskRunId());
        }

        @Override
        public Optional<String> get(String key) {
            return checkpointStateStore.get(tenantId, taskRunInfo.executionId(), taskRunInfo.taskRunId(), key).map(it -> it.value());
        }

        @Override
        public void set(String key, String value) {
            var checkpoint = new Checkpoint(
                tenantId,
                taskRunInfo.executionId(),
                taskRunInfo.taskRunId(),
                key,
                value
            );

            checkpointStateStore.set(checkpoint);
        }

    }
}
