package io.kestra.core.runners;

/**
 * Service interface for accessing Worker Groups data from a Kestra's Executor service.
 *
 * @see io.kestra.core.models.tasks.WorkerGroup
 */
public interface WorkerGroupExecutorInterface {

    /**
     * Checks whether a Worker Group exists for the given key.
     *
     * @param key The Worker Group's key - can be {@code null}.
     * @return {@code true} if the worker group exists, or is {@code null}, {@code false} otherwise.
     */
    boolean isWorkerGroupExistForKey(String key);

    /**
     * Checks whether the Worker Group is available.
     * <p>
     * A worker group is available if at-least one worker is running for that group.
     *
     * @param key The Worker Group's key - can be {@code null}.
     * @return {@code true} if the worker group is available, or is {@code null}, {@code false} otherwise.
     */
     boolean isWorkerGroupAvailableForKey(String key);
}
