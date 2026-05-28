package io.kestra.worker.fetchers;

import java.time.Duration;

import io.kestra.core.worker.models.WorkerContext;

/**
 * Abstraction for sourcing {@code WorkerJob}s into a worker's in-memory queue.
 * <p>
 * Implementations may pull from a gRPC bidirectional stream (the default
 * {@link WorkerJobFetcher}), subscribe to a backing queue directly
 * ({@code DirectQueueJobFetcher} for the SystemWorker), or any other transport.
 * <p>
 * Implementations must be {@link Runnable} so they can be submitted to the
 * worker's IO thread executor.
 */
public interface JobFetcher extends Runnable {

    /**
     * Initialize the fetcher and bind it to the given {@link WorkerContext}.
     * <p>
     * Must be called before the fetcher loop is started.
     */
    void init(WorkerContext context);

    /**
     * Pause job intake. Any in-flight jobs continue running.
     */
    void pause();

    /**
     * Resume job intake after a {@link #pause()}.
     */
    void resume();

    /**
     * Stop the fetcher and release any resources, waiting up to {@code timeout}
     * for in-flight work to complete.
     *
     * @param timeout the maximum time to wait. {@link Duration#ZERO} means
     *                signal stop and return immediately.
     */
    void stop(Duration timeout);
}
