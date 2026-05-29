package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.server.Service;

/**
 * The worker service interface.
 */
public interface Worker extends Service {
    String EXECUTOR_NAME = "worker";

    /**
     * The default number of threads for the worker service.
     * Only used for display/documentation of commands as in runtime KestraContext.getAllocatedCpuCores() is used instead
     */
    static int defaultNumThreads() {
        return Runtime.getRuntime().availableProcessors() * 8;
    }

    /**
     * Starts the worker service.
     *
     * @param numThreads the number of threads.
     */
    void start(int numThreads);

    /**
     * Gets the list of tasks currently running.
     *
     * @return the list of {@link WorkerJob}.
     */
    List<WorkerJob> getRunningJobs();
}
