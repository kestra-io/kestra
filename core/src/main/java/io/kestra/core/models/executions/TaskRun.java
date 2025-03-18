package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
public class TaskRun implements TenantInterface {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId;

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

    @With
    String value;

    @With
    List<TaskRunAttempt> attempts;

    @With
    @JsonInclude(JsonInclude.Include.ALWAYS) // always include outputs so it's easier to reason about in expressions
    Map<String, Object> outputs;

    @NotNull
    State state;

    @With
    Integer iteration;

    @With
    Boolean dynamic;

    @Deprecated
    public void setItems(String items) {
        // no-op for backward compatibility
    }

    public TaskRun withState(State.Type state) {
        return new TaskRun(
            this.tenantId,
            this.id,
            this.executionId,
            this.namespace,
            this.flowId,
            this.taskId,
            this.parentTaskRunId,
            this.value,
            this.attempts,
            this.outputs,
            this.state.withState(state),
            this.iteration,
            this.dynamic
        );
    }

    public TaskRun replaceState(State newState) {
        return new TaskRun(
            this.tenantId,
            this.id,
            this.executionId,
            this.namespace,
            this.flowId,
            this.taskId,
            this.parentTaskRunId,
            this.value,
            this.attempts,
            this.outputs,
            newState,
            this.iteration,
            this.dynamic
        );
    }

    public TaskRun fail() {
        var attempt = TaskRunAttempt.builder().state(new State(State.Type.FAILED)).build();
        List<TaskRunAttempt> newAttempts = this.attempts == null ? new ArrayList<>(1) : this.attempts;
        newAttempts.add(attempt);

        return new TaskRun(
            this.tenantId,
            this.id,
            this.executionId,
            this.namespace,
            this.flowId,
            this.taskId,
            this.parentTaskRunId,
            this.value,
            newAttempts,
            this.outputs,
            this.state.withState(State.Type.FAILED),
            this.iteration,
            this.dynamic
        );
    }

    public TaskRun forChildExecution(Map<String, String> remapTaskRunId, String executionId, State state) {
        return TaskRun.builder()
            .tenantId(this.getTenantId())
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
            .iteration(this.getIteration())
            .build();
    }

    public static TaskRun of(Execution execution, ResolvedTask resolvedTask) {
        return TaskRun.builder()
            .tenantId(execution.getTenantId())
            .id(IdUtils.create())
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId(resolvedTask.getTask().getId())
            .parentTaskRunId(resolvedTask.getParentId())
            .value(resolvedTask.getValue())
            .iteration(resolvedTask.getIteration())
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

        if (taskRunBuilder.attempts == null || taskRunBuilder.attempts.isEmpty()) {
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
        return this.getId().equals(taskRun.getId()) &&
            ((this.getValue() == null && taskRun.getValue() == null) || (this.getValue() != null && this.getValue().equals(taskRun.getValue()))) &&
            ((this.getIteration() == null && taskRun.getIteration() == null) || (this.getIteration() != null && this.getIteration().equals(taskRun.getIteration()))) ;
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
            ", attempts=" + this.getAttempts() +
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

    /**
     * This method is used when the retry is apply on a task
     * but the retry type is NEW_EXECUTION
     *
     * @param retry Contains the retry configuration
     * @param execution Contains the attempt number and original creation date
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry, Execution execution) {
        if (retry.getMaxAttempt() != null && execution.getMetadata().getAttemptNumber() >= retry.getMaxAttempt()) {

            return null;
        }
        Instant base = this.lastAttempt().getState().maxDate();
        Instant nextDate = retry.nextRetryDate(execution.getMetadata().getAttemptNumber(), base);
        if (retry.getMaxDuration() != null && nextDate.isAfter(execution.getMetadata().getOriginalCreatedDate().plus(retry.getMaxDuration()))) {

            return null;
        }

        return nextDate;
    }

    /**
     * This method is used when the Retry definition comes from the flow
     * @param retry The retry configuration
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry) {
        if (this.attempts == null || this.attempts.isEmpty() || (retry.getMaxAttempt() != null && this.attemptNumber() >= retry.getMaxAttempt())) {

            return null;
        }
        Instant base = this.lastAttempt().getState().maxDate();
        Instant nextDate = retry.nextRetryDate(this.attempts.size(), base);
        if (retry.getMaxDuration() != null && nextDate.isAfter(this.attempts.getFirst().getState().minDate().plus(retry.getMaxDuration()))) {

            return null;
        }

        return nextDate;
    }

    public boolean shouldBeRetried(AbstractRetry retry) {
        if (retry == null) {
            return false;
        }
        return this.nextRetryDate(retry) != null;
    }

    public TaskRun incrementIteration() {
        int iteration = this.iteration == null ? 1 : this.iteration;
        return this.toBuilder()
            .iteration(iteration + 1)
            .build();
    }

    public TaskRun resetAttempts() {
        return this.toBuilder()
            .state(new State(State.Type.CREATED, List.of(this.state.getHistories().getFirst())))
            .attempts(null)
            .build();
    }

}
