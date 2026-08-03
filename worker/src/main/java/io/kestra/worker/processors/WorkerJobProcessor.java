package io.kestra.worker.processors;

import io.kestra.core.runners.WorkerJob;

import io.micronaut.core.annotation.Blocking;

/**
 * A processor responsible for executing a specific {@link WorkerJob}.
 *
 * @param <T> the type of {@link WorkerJob} to be processed
 */
public interface WorkerJobProcessor<T extends WorkerJob> {

    /**
     * Processes the given {@link WorkerJob}.
     * <p>
     * This method will block the calling thread until the job has been completed or terminated.
     * Only one job may be processed at a time per {@code WorkerJobProcessor} instance.
     *
     * @param workerJob the {@link WorkerJob} to be executed
     */
    @Blocking
    void process(T workerJob);

    /**
     * Signals the currently running job to stop, if any.
     * <p>
     * If no job is currently running, the method returns immediately without any side effects.
     */
    void stop();

    /**
     * Signals the currently running job to be killed (execution killed event).
     * <p>
     * Unlike {@link #stop()}, which is used for graceful shutdown, this method is invoked
     * when an execution kill event is received and the running task must be terminated.
     * <p>
     * If no job is currently running, the method returns immediately without any side effects.
     */
    void kill();

    /**
     * Signals that the currently running job is being forcibly interrupted by the worker shutdown,
     * because the termination grace period elapsed (or a force shutdown was requested).
     * <p>
     * Unlike {@link #stop()} — the cooperative drain signal sent at the start of shutdown, which lets an
     * in-flight task keep running and possibly reach its own terminal state — this marks the job as
     * <em>interrupted by the shutdown</em> so a task torn down here can be told apart from one that failed
     * on its own during the drain window.
     * <p>
     * If no job is currently running, the method returns immediately without any side effects.
     */
    void signalShutdownInterrupt();
}
