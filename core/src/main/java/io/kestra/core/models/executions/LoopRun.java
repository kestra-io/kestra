package io.kestra.core.models.executions;

import jakarta.annotation.Nullable;

import java.util.List;

public record LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents) {
    public record Parent(int index, @Nullable String key, String value) {}
}
