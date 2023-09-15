package io.kestra.core.models.executions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.With;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.utils.IdUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
public class TaskRun {
    @NotNull
    String id;

    @NotNull
    String executionId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    String taskId;

    String parentTaskRunId;

    String value;

    @With
    List<TaskRunAttempt> attempts;

    @With
    Map<String, Object> outputs;

    @NotNull
    State state;

    public void destroyOutputs() {
        // DANGER ZONE: this method is only used to deals with issues with messages too big that must be stripped down
        // to avoid crashing the platform. Don't use it for anything else.
        this.outputs = Collections.emptyMap();
        this.state = this.state.withState(State.Type.FAILED);
    }

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

    public TaskRun forChildExecution(Map<String, String> remapTaskRunId, String executionId, State state) {
        return TaskRun.builder()
            .id(remapTaskRunId.get(this.getId()))
            .executionId(executionId != null ? executionId : this.getExecutionId())
            .namespace(this.getNamespace())
            .flowId(this.getFlowId())
            .taskId(this.getTaskId())
            .parentTaskRunId(this.getParentTaskRunId() != null ? remapTaskRunId.get(this.getParentTaskRunId()) : null)
            .value(this.getValue())
            .attempts(this.getAttempts())
            .outputs(this.getOutputs())
            .state(state == null ? this.getState() : state)
            .build();
    }

    public static TaskRun of(Execution execution, ResolvedTask resolvedTask) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId(resolvedTask.getTask().getId())
            .parentTaskRunId(resolvedTask.getParentId())
            .value(resolvedTask.getValue())
            .state(new State())
            .build();
    }

    public int attemptNumber() {
        if (this.attempts == null) {
            return 0;
        }

        return this.attempts.size();
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

    public TaskRun onRunningResend() {
        TaskRunBuilder taskRunBuilder = this.toBuilder();

        if (taskRunBuilder.attempts == null || taskRunBuilder.attempts.size() == 0) {
            taskRunBuilder.attempts = new ArrayList<>();

            taskRunBuilder.attempts.add(TaskRunAttempt.builder()
                .state(new State(this.state, State.Type.KILLED))
                .build()
            );
        } else {
            ArrayList<TaskRunAttempt> taskRunAttempts = new ArrayList<>(taskRunBuilder.attempts);
            TaskRunAttempt lastAttempt = taskRunAttempts.get(taskRunBuilder.attempts.size() - 1);
            if (!lastAttempt.getState().isTerminated()) {
                taskRunAttempts.set(taskRunBuilder.attempts.size() - 1, lastAttempt.withState(State.Type.KILLED));
            } else {
                taskRunAttempts.add(TaskRunAttempt.builder()
                    .state(new State().withState(State.Type.KILLED))
                    .build()
                );
            }

            taskRunBuilder.attempts(taskRunAttempts);
        }

        return taskRunBuilder.build();
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
