package io.kestra.core.plugins.endpoint;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.TaskOutputService;

import java.util.Map;

public class DefaultTaskRunOutputsFetcher implements TaskRunOutputsFetcher {
    private final TaskOutputService taskOutputService;
    private final TaskRun taskRun;

    public DefaultTaskRunOutputsFetcher(TaskOutputService taskOutputService, TaskRun taskRun) {
        this.taskOutputService = taskOutputService;
        this.taskRun = taskRun;
    }

    @Override
    public Map<String, Object> get() {
        try {
            return taskOutputService.getOutputs(taskRun);
        } catch (InternalException e) {
            throw new KestraRuntimeException("Cannot read the outputs of task run '%s'.".formatted(taskRun.getId()), e);
        }
    }
}
