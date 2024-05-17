package io.kestra.core.models;

import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.runners.RunContext;

/**
 * Interface that provides hooks methods on the lifecycle of jobs running on workers.
 *
 * @see io.kestra.core.models.tasks.RunnableTask
 * @see io.kestra.core.models.triggers.RealtimeTriggerInterface
 */
public interface WorkerJobLifecycle {

    /**
     * Forces termination of the underlying job and its associated processes.
     *
     * <p>This method is invoked when the job is killed or timeout.
     *
     * <p>Note that this method may be invoked from a different thread than the one running the job.
     */
    default void kill() { /* noop */ }

    /**
     * Signals the underlying job to stop.
     *
     * <p>This method is invoked when the server is shutting down.
     * The implementation of this method MUST be non-blocking. Thus, it is not required that the job has fully
     * stopped when returning from this method. For example, this method could set a flag that will force
     * the {@link RunnableTask#run(RunContext)} to return immediately, or a {@link RealtimeTriggerInterface} to stop publishing execution.
     *
     * <p>Note that this method may be invoked from a different thread than the one running the job.
     */
    default void stop() { /* noop */ }
    
}
