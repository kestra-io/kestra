package org.kestra.core.models.executions;

import com.devskiller.friendly_id.FriendlyId;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class TaskRun {
    private String id;

    @NotNull
    private String executionId;

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private String taskId;

    private String parentTaskRunId;

    private String value;

    @With
    private List<TaskRunAttempt> attempts;

    @With
    private Map<String, Object> outputs;

    @NotNull
    private State state;

    public TaskRun withState(State.Type state) {
        return new TaskRun(
            this.id,
            this.executionId,
            this.namespace,
            this.flowId,
            this.taskId,
            this.parentTaskRunId,
            this.value,
            this.attempts,
            this.outputs,
            this.state.withState(state)
        );
    }

    public TaskRun forChildExecution(String id, String executionId, String parentTaskRunId, State state) {
        return TaskRun.builder()
            .id(id)
            .executionId(executionId)
            .namespace(this.getNamespace())
            .flowId(this.getFlowId())
            .taskId(this.getTaskId())
            .parentTaskRunId(parentTaskRunId)
            .value(this.getValue())
            .attempts(this.getAttempts())
            .outputs(this.getOutputs())
            .state(state)
            .build();
    }

    public static TaskRun of(Execution execution, ResolvedTask resolvedTask) {
        return TaskRun.builder()
            .id(FriendlyId.createFriendlyId())
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId(resolvedTask.getTask().getId())
            .parentTaskRunId(resolvedTask.getParentId())
            .value(resolvedTask.getValue())
            .state(new State())
            .build();
    }

    public TaskRunAttempt lastAttempt() {
        if (this.attempts == null) {
            return null;
        }

        return this
            .attempts
            .stream()
            .reduce((a, b) -> b)
            .orElse(null);
    }

    public boolean isSame(TaskRun taskRun) {
        return this.getId().equals(taskRun.getId()) && (
            (this.getValue() == null && taskRun.getValue() == null) ||
                (this.getValue() != null && this.getValue().equals(taskRun.getValue()))
        );
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
            ", attemps=" + this.getAttempts() +
            ")";
    }

    public String toStringState() {
        return "TaskRun(" +
            "id=" + this.getId() +
            ", taskId=" + this.getTaskId() +
            ", value=" + this.getValue() +
            ", state=" + this.getState().getCurrent().toString() +
            ")";
    }
}
