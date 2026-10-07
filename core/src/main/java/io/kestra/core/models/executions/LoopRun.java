package io.kestra.core.models.executions;

import java.util.List;

import jakarta.annotation.Nullable;

public record LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents, @Nullable String rootExecutionId) {
    public LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents) {
        this(parent, taskId, taskRunId, index, key, value, parents, null);
    }

    public record Parent(String executionId, String taskId, int index, @Nullable String key, String value) {
    }
}
