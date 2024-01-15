package io.kestra.core.models.tasks;

import io.kestra.core.runners.RunContext;

/**
 * Interface for tasks that are run in the Worker.
 */
public interface RunnableTask <T extends Output> {
    /**
     * This method is called inside the Worker to run (execute) the task.
     */
    T run(RunContext runContext) throws Exception;
}
