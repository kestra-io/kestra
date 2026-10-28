package io.kestra.webserver.models.api;

import java.util.List;

import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.TaskRunDefinitionEdit;
import io.kestra.core.models.flows.State;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record ApiTaskRun(@NotNull String id,
    @NotNull String taskId,
    String parentTaskRunId,
    String value,
    List<TaskRunAttempt> attempts,
    List<AssetsInOut> assetEmits,
    @NotNull State state,
    Integer iteration,
    Boolean dynamic,
    Boolean forceExecution,
    @Nullable TaskRunDefinitionEdit definitionEdit) {
    public static ApiTaskRun of(TaskRun taskRun) {
        return new ApiTaskRun(
            taskRun.getId(), taskRun.getTaskId(), taskRun.getParentTaskRunId(), taskRun.getValue(), taskRun.getAttempts(), taskRun.getAssetEmits(), taskRun.getState(), taskRun.getIteration(),
            taskRun.getDynamic(), taskRun.getForceExecution(), taskRun.getDefinitionEdit()
        );
    }
}
