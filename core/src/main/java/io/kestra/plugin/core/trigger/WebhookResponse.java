package io.kestra.plugin.core.trigger;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record WebhookResponse(
    String tenantId,
    String id,
    String namespace,
    String flowId,
    Integer flowRevision,
    ExecutionTrigger trigger,
    Map<String, Object> outputs,
    List<Label> labels,
    State state,
    URI url
) {
    public static WebhookResponse fromExecution(Execution execution, URI url) {
        return new WebhookResponse(
            execution.getTenantId(),
            execution.getId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getFlowRevision(),
            execution.getTrigger(),
            execution.getOutputs(),
            execution.getLabels(),
            execution.getState(),
            url
        );
    }
}