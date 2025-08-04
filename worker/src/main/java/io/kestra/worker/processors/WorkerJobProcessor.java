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
}
