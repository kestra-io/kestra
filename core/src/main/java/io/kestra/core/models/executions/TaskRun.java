package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

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
    @Nullable
    AssetsInOut assets;

    @NotNull
    State state;

    @With
    Integer iteration;

    @With
    Boolean dynamic;

    // Set it to true to force execution even if the execution is killed
    @With
    Boolean forceExecution;

    /**
     * @deprecated should not be used anymore, but we keep it to be able to read the existing outputs from V1 inside the migration script.
     */
    @Hidden
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Nullable
    @Schema(implementation = Object.class)
    @Deprecated(forRemoval = true, since = "2.0.0")
    Variables outputs;

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
            this.assets,
            this.state.withState(state),
            this.iteration,
            this.dynamic,
            this.forceExecution,
            this.outputs
        );
    }

    public TaskRun withStateAndAttempt(State.Type state) {
        List<TaskRunAttempt> newAttempts = new ArrayList<>(this.attempts != null ? this.attempts : List.of());

        if (newAttempts.isEmpty()) {
            newAttempts.add(TaskRunAttempt.builder().state(new State(state)).build());
        } else {
            TaskRunAttempt updatedLast = newAttempts.getLast().withState(state);
            newAttempts.set(newAttempts.size() - 1, updatedLast);
        }

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
            this.assets,
            this.state.withState(state),
            this.iteration,
            this.dynamic,
            this.forceExecution,
            this.outputs
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
            this.assets,
            this.state.withState(State.Type.FAILED),
            this.iteration,
            this.dynamic,
            this.forceExecution,
            this.outputs
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
            .assets(this.getAssets())
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
        if (this.attempts == null || this.attempts.isEmpty()) {
            return null;
        }

        return this.attempts.getLast();
    }

    public TaskRun onRunningResend() {
        TaskRunBuilder taskRunBuilder = this.toBuilder();

        if (taskRunBuilder.attempts == null || taskRunBuilder.attempts.isEmpty()) {
            taskRunBuilder.attempts = new ArrayList<>();

            taskRunBuilder.attempts.add(
                TaskRunAttempt.builder()
                    .state(new State(this.state, State.Type.RESUBMITTED))
                    .build()
            );
        } else {
            ArrayList<TaskRunAttempt> taskRunAttempts = new ArrayList<>(taskRunBuilder.attempts);
            TaskRunAttempt lastAttempt = taskRunAttempts.get(taskRunBuilder.attempts.size() - 1);
            if (!lastAttempt.getState().isTerminated()) {
                taskRunAttempts.set(taskRunBuilder.attempts.size() - 1, lastAttempt.withState(State.Type.RESUBMITTED));
            } else {
                taskRunAttempts.add(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.RESUBMITTED))
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
            ((this.getIteration() == null && taskRun.getIteration() == null) || (this.getIteration() != null && this.getIteration().equals(taskRun.getIteration())));
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
            ", assets=" + this.getAssets() +
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
        if (this.attempts == null || this.attempts.isEmpty() || retry.getMaxAttempts() != null && execution.getMetadata().getAttemptNumber() >= retry.getMaxAttempts()) {
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
     *
     * @param retry The retry configuration
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry) {
        if (this.attempts == null || this.attempts.isEmpty() || (retry.getMaxAttempts() != null && this.attemptNumber() >= retry.getMaxAttempts())) {

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
        int iteration = this.iteration == null ? 0 : this.iteration;
        return this.toBuilder()
            .iteration(iteration + 1)
            .build();
    }

    public TaskRun resetAttempts() {
        State.Type lastCreationState = this.state.getHistories()
            .reversed()
            .stream()
            .filter(history -> history.getState().isCreated())
            .findFirst().get()
            .getState();
        return this.toBuilder()
            .state(new State(lastCreationState, this.state.getHistories()))
            .attempts(null)
            .build();
    }

    public TaskRun addAttempt(TaskRunAttempt attempt) {
        if (this.attempts == null) {
            this.attempts = new ArrayList<>();
        }
        this.attempts.add(attempt);
        return this;
    }
}
