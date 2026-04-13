package io.kestra.core.runners;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;

public record SubflowExecutionEnd(
    Execution childExecution,
    String parentExecutionId,
    String taskRunId,
    String taskId,
    State.Type state,
    @JsonInclude(JsonInclude.Include.ALWAYS) Map<String, Object> outputs) implements HasUID, DispatchEvent {

    public String toStringState() {
        return "SubflowExecutionEnd(" +
            "childExecutionId=" + childExecution.getId() +
            ", parentExecutionId=" + parentExecutionId +
            ", taskId=" + taskId +
            ", taskRunId=" + taskRunId +
            ", state=" + state +
            ")";
    }

    @Override
    public String uid() {
        return parentExecutionId;
    }

    @Override
    public String key() {
        return uid();
    }
}
