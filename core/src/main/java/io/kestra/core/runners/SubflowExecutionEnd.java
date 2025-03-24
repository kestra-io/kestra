package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubflowExecutionEnd {
    private Execution childExecution;
    private String parentExecutionId;
    private String taskRunId;
    private String taskId;
    private State.Type state;
    private Map<String, Object> outputs;

    public String toStringState() {
        return "SubflowExecutionEnd(" +
            "childExecutionId=" + this.getChildExecution().getId() +
            ", parentExecutionId=" + this.getParentExecutionId() +
            ", taskId=" + this.getTaskId() +
            ", taskRunId=" + this.getTaskRunId() +
            ", state=" + this.getState().toString() +
            ")";
    }
}
