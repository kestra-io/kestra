package io.kestra.core.models.tasks;

import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;

/**
 * Interface for tasks that modify the execution at runtime.
 */
public interface ExecutionUpdatableTask {
    /**
     * Update the execution.
     * If the returned execution is in the KILLED state, the executor will then send a killing event.
     */
    Execution update(Execution execution, RunContext runContext) throws Exception;

    /**
     * Resolve the state of a flowable task.
     */
    default Optional<State.Type> resolveState(RunContext runContext, Execution execution) throws IllegalVariableEvaluationException {
        return Optional.empty();
    }
}
