package io.kestra.webserver.models.api;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiLightExecution(String tenantId,
                                String id,
                                String namespace,
                                String flowId,
                                Integer flowRevision,
                                Map<String, Object> inputs,
                                Map<String, Object> outputs,
                                List<Label> labels,
                                State state,
                                String parentId,
                                String originalId,
                                ExecutionTrigger trigger,
                                Instant scheduleDate,
                                ExecutionKind kind,
                                LoopRun loopRun) {
    public static ApiLightExecution of(Execution execution) {
        return new ApiLightExecution(execution.getTenantId(), execution.getId(), execution.getNamespace(), execution.getFlowId(), execution.getFlowRevision(), execution.getInputs(), execution.getOutputs(), execution.getLabels(), execution.getState(), execution.getParentId(), execution.getOriginalId(), execution.getTrigger(), execution.getScheduleDate(), execution.getKind(), execution.getLoopRun());
    }
}
