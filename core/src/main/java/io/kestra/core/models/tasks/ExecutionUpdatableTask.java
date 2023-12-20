package io.kestra.core.models.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;

/**
 * Interface for tasks that modify the execution at runtime.
 */
public interface ExecutionUpdatableTask {
    Execution update(Execution execution, RunContext runContext) throws Exception;
}
