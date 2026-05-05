package io.kestra.webserver.models.api;

import io.kestra.core.debug.Breakpoint;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.utils.ListUtils;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiExecution(@NotNull String tenantId,
                           @NotNull String id,
                           @NotNull String namespace,
                           @NotNull String flowId,
                           @NotNull Integer flowRevision,
                           List<ApiTaskRun> taskRunList,
                           Map<String, Object> inputs,
                           Map<String, Object> outputs,
                           List<Label> labels,
                           Map<String, Object> variables,
                           @NotNull State state,
                           String parentId,
                           @NotNull String originalId,
                           ExecutionTrigger trigger,
                           @NotNull ExecutionMetadata metadata,
                           Instant scheduleDate,
                           String traceParent,
                           List<TaskFixture> fixtures,
                           ExecutionKind kind,
                           List<Breakpoint> breakpoints,
                           LoopRun loopRun) {

    public static ApiExecution of(Execution execution) {
        return new ApiExecution(execution.getTenantId(), execution.getId(), execution.getNamespace(), execution.getFlowId(), execution.getFlowRevision(), ListUtils.emptyOnNull(execution.getTaskRunList()).stream().map(ApiTaskRun::of).toList(), execution.getInputs(), execution.getOutputs(), execution.getLabels(), execution.getVariables(), execution.getState(), execution.getParentId(), execution.getOriginalId(), execution.getTrigger(), execution.getMetadata(), execution.getScheduleDate(), execution.getTraceParent(), execution.getFixtures(), execution.getKind(), execution.getBreakpoints(), execution.getLoopRun());
    }
}
