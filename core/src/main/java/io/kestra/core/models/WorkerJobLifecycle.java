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
     * This method is invoked when the job is killed.
     * <p>
     * Must be non-blocking.
     */
    default void kill() { /* noop */ }

    /**
     * Signal the job to stop.
     * <p>
     * This method is invoked when the Worker running the job is shutting down.
     * The job must complete and return as soon as possible from its main method.
     * <p>
     * Must be non-blocking.
     *
     */
    default void stop() { /* noop */ }
}
