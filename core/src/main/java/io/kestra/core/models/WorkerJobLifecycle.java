package io.kestra.core.models;

/**
 * Interface that provides hooks methods on the lifecycle of jobs running on workers.
 *
 * @see io.kestra.core.models.tasks.RunnableTask
 */
public interface WorkerJobLifecycle {

    /**
     * Forces termination of the underlying job and its associated processes.
     * <p>
     * This method is invoked when the job is killed or timeout.
     */
    default void kill() { /* noop */ }
    
}
