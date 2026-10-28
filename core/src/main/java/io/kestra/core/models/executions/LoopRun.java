package io.kestra.core.models.executions;

import java.util.List;

import jakarta.annotation.Nullable;

public record LoopRun(
    Execution parent,
    String taskId,
    String taskRunId,
    int index,
    @Nullable String key,
    String value,
    List<Parent> parents,
    @Nullable String rootExecutionId,
    boolean parseValues) {
    public LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents, boolean parseValues) {
        this(parent, taskId, taskRunId, index, key, value, parents, null, parseValues);
    }

    public LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents) {
        this(parent, taskId, taskRunId, index, key, value, parents, null, false);
    }

    public record Parent(@Nullable String executionId, @Nullable String taskId, int index, @Nullable String key, String value, boolean parseValues) {
        public Parent(int index, @Nullable String key, String value, boolean parseValues) {
            this(null, null, index, key, value, parseValues);
        }

        public Parent(int index, @Nullable String key, String value) {
            this(null, null, index, key, value, false);
        }

        public Parent(@Nullable String executionId, @Nullable String taskId, int index, @Nullable String key, String value) {
            this(executionId, taskId, index, key, value, false);
        }
    }
}
