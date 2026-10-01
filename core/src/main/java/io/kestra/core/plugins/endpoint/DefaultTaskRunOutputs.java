package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;

import java.util.Map;

public class DefaultTaskRunOutputs implements TaskRunOutputs {
    private final Execution execution;
    private final String taskRunId;

    public DefaultTaskRunOutputs(Execution execution, String taskRunId) {
        this.execution = execution;
        this.taskRunId = taskRunId;
    }

    @Override
    public Map<String, Object> get() {
        return execution.findTaskRunByTaskRunIdIfPresent(taskRunId)
            .map(TaskRun::getOutputs)
            .orElseGet(Map::of);
    }
}
