package io.kestra.core.runners;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

import java.util.Set;

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

    /**
     * Returns the list of all existing Worker Groups' Keys.
     *
     * @return The set of Worker Groups' Keys.
     */
    Set<String> listAllWorkerGroupKeys();

    /**
     * Default {@link WorkerGroupExecutorInterface} implementation.
     * This class is only used if no other implementation exist.
     */
    @Singleton
    @Secondary
    class DefaultWorkerGroupExecutorInterface implements WorkerGroupExecutorInterface {

        @Override
        public boolean isWorkerGroupExistForKey(String key) {
            return true;
        }

        @Override
        public boolean isWorkerGroupAvailableForKey(String key) {
            return true;
        }

        @Override
        public Set<String> listAllWorkerGroupKeys() {
            return Set.of();
        }
    }
}
