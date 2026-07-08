package io.kestra.core.models.executions;

import java.util.List;

import jakarta.annotation.Nullable;

public record LoopRun(Execution parent, String taskId, String taskRunId, int index, @Nullable String key, String value, List<Parent> parents) {
    public record Parent(int index, @Nullable String key, String value) {
    }
}
