package io.kestra.core.models.executions;

import java.util.List;

import jakarta.annotation.Nullable;

public record LoopRun(Execution parent, @Nullable String rootExecutionId, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents) {
    public record Parent(String executionId, String taskId, int index, @Nullable String key, String value) {
    }
}
