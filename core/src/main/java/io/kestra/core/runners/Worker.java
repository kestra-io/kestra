package io.kestra.core.runners;

import io.kestra.core.server.Service;

import java.util.List;

/**
 * The worker service interface.
 */
public interface Worker extends Service {
    String EXECUTOR_NAME = "worker";

    static int defaultNumThreads() {
        return Math.min(Runtime.getRuntime().availableProcessors() * 8, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Starts the worker service.
     *
     * @param numThreads     the number of threads.
     * @param workerGroupKey the worker group key.
     */
    void start(int numThreads, String workerGroupKey);

    /**
     * Gets the list of tasks currently running.
     *
     * @return the list of {@link WorkerJob}.
     */
    List<WorkerJob> getRunningJobs();
}
