package io.kestra.core.services;

import java.util.Optional;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/** Terminates an execution in a chosen state, cascading it from a task run up through its parents; shared by {@code Exit} and {@code Approval}. */
@Slf4j
public final class ExecutionTerminator {
    private ExecutionTerminator() {
    }

    /** {@code KILLED} bypasses {@code startingTaskRun}: the caller must emit the kill event that stops running task runs. */
    public static Execution terminate(Execution execution, @Nullable TaskRun startingTaskRun, State.Type state) {
        if (state == State.Type.KILLED) {
            return execution.withState(State.Type.KILLED);
        }

        return cascade(execution, startingTaskRun, state).withState(state);
    }

    /** Same cascade as {@link #terminate} but leaves the execution state alone, so the flow can still run its {@code finally} and end itself. */
    public static Execution cascade(Execution execution, @Nullable TaskRun startingTaskRun, State.Type state) {
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
            .orElse(execution);
    }
}
