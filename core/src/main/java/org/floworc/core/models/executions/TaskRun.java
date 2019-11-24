package org.floworc.core.models.executions;

import com.devskiller.friendly_id.FriendlyId;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.ResolvedTask;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class TaskRun {
    private String id;

    @NotNull
    private String executionId;

    @NotNull
    private String flowId;

    @NotNull
    private String taskId;

    private String parentTaskRunId;

    private String value;

    @With
    private List<LogEntry> logs;

    @With
    private Map<String, Object> outputs;

    @With
    private List<MetricEntry> metrics;

    @NotNull
    private State state;

    public TaskRun withState(State.Type state) {
        return new TaskRun(
            this.id,
            this.executionId,
            this.flowId,
            this.taskId,
            this.parentTaskRunId,
            this.value,
            this.logs,
            this.outputs,
            this.metrics,
            this.state.withState(state)
        );
    }

    public static TaskRun of(Execution execution, ResolvedTask resolvedTask) {
        return TaskRun.builder()
            .id(FriendlyId.createFriendlyId())
            .executionId(execution.getId())
            .flowId(execution.getFlowId())
            .taskId(resolvedTask.getTask().getId())
            .parentTaskRunId(resolvedTask.getParentId())
            .value(resolvedTask.getValue())
            .state(new State())
            .build();
    }

    public String toString(boolean pretty) {
        if (!pretty) {
            return super.toString();
        }

        return "TaskRun(" +
            "id=" + this.getId() +
            ", taskId=" + this.getTaskId() +
            ", value=" + this.getValue() +
            ", parentTaskRunId=" + this.getParentTaskRunId() +
            ", state=" + this.getState().getCurrent().toString() +
            ", outputs=" + this.getOutputs() +
            ", logs=" + this.getLogs() +
            ", metrics=" + this.getMetrics() +
            ")";
    }
}
