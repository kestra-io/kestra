package io.kestra.webserver.models.api;

import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApiTaskRun(@NotNull String id,
                         @NotNull String taskId,
                         String parentTaskRunId,
                         String value,
                         List<TaskRunAttempt> attempts,
                         AssetsInOut assets,
                         @NotNull State state,
                         Integer iteration,
                         Boolean dynamic,
                         Boolean forceExecution) {
    public static ApiTaskRun of(TaskRun taskRun) {
        return new ApiTaskRun(taskRun.getId(), taskRun.getTaskId(), taskRun.getParentTaskRunId(), taskRun.getValue(), taskRun.getAttempts(), taskRun.getAssets(), taskRun.getState(), taskRun.getIteration(), taskRun.getDynamic(), taskRun.getForceExecution());
    }
}
