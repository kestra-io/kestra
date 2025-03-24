package io.kestra.core.models.tasks;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;

import java.util.Optional;

/**
 * Interface for tasks that modify the execution at runtime.
 */
public interface ExecutionUpdatableTask {
    Execution update(Execution execution, RunContext runContext) throws Exception;

    /**
     * Resolve the state of a flowable task.
     */
    default Optional<State.Type> resolveState(RunContext runContext, Execution execution) throws IllegalVariableEvaluationException {
        return Optional.empty();
    }
}
