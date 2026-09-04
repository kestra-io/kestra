package io.kestra.webserver.models.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.ListUtils;

import jakarta.validation.constraints.NotNull;

public record ApiLightExecution(@NotNull String tenantId,
    @NotNull String id,
    @NotNull String namespace,
    @NotNull String flowId,
    @NotNull Integer flowRevision,
    LastTaskRun lastTaskRun,
    Map<String, Object> inputs,
    List<Label> labels,
    @NotNull State state,
    String parentId,
    @NotNull String originalId,
    ExecutionTrigger trigger,
    Instant scheduleDate,
    ExecutionKind kind,
    LoopRun loopRun) {

    /**
     * Summary of the execution's most recent task run, so that the executions list can show it without
     * carrying the whole task run list — which this DTO exists to keep out of the list payload.
     */
    public record LastTaskRun(@NotNull String taskId, @NotNull Integer attempts) {
        public static LastTaskRun of(TaskRun taskRun) {
            return new LastTaskRun(taskRun.getTaskId(), taskRun.attemptNumber());
        }
    }

    public static ApiLightExecution of(Execution execution) {
        List<TaskRun> taskRunList = execution.getTaskRunList();

        return new ApiLightExecution(
            execution.getTenantId(), execution.getId(), execution.getNamespace(), execution.getFlowId(), execution.getFlowRevision(),
            ListUtils.isEmpty(taskRunList) ? null : LastTaskRun.of(taskRunList.getLast()), execution.getInputs(),
            execution.getLabels(), execution.getState(), execution.getParentId(), execution.getOriginalId(), execution.getTrigger(), execution.getScheduleDate(), execution.getKind(),
            execution.getLoopRun()
        );
    }
}
