package io.kestra.core.models.executions;

import java.util.List;

public record LoopRun(String executionId, String taskId, String taskRunId, String value, int index, List<Parent> parents) {
    public record Parent(String value, int index) {}
}
