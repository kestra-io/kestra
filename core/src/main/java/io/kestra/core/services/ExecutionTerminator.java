package io.kestra.core.services;

import java.util.Optional;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Terminates an execution in a chosen state, starting from a given task run and cascading the same
 * state up through its parents. Shared by {@link io.kestra.plugin.core.execution.Exit} and by
 * {@code Approval}'s {@code SUCCEED}/{@code CANCEL}/{@code KILL} behaviors, so the two never drift.
 */
@Slf4j
public final class ExecutionTerminator {
    private ExecutionTerminator() {
    }

    /**
     * {@code KILLED} bypasses {@code startingTaskRun} entirely: the caller must separately emit the kill event that stops running task runs.
     */
    public static Execution terminate(Execution execution, @Nullable TaskRun startingTaskRun, State.Type state) {
        if (state == State.Type.KILLED) {
            return execution.withState(State.Type.KILLED);
        }

        return Optional.ofNullable(startingTaskRun)
            .map(taskRun -> {
                try {
                    TaskRun newTaskRun = taskRun.withState(state);
                    Execution newExecution = execution.withTaskRun(newTaskRun);
                    while (newTaskRun.getParentTaskRunId() != null) {
                        newTaskRun = newExecution.findTaskRunByTaskRunId(newTaskRun.getParentTaskRunId()).withStateAndAttempt(state);
                        newExecution = newExecution.withTaskRun(newTaskRun);
                    }
                    return newExecution;
                } catch (InternalException e) {
                    log.warn("Unable to update the taskrun state", e);
                    return execution;
                }
            })
            .orElse(execution)
            .withState(state);
    }
}
