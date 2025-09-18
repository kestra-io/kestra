package io.kestra.executor;

import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;

/**
 * State store containing all workers' jobs in RUNNING state.
 *
 * @see WorkerJob
 */
public interface WorkerJobRunningStateStore {

    /**
     * Deletes a running worker job for the given key.
     *
     * <p>
     *     A key can be a {@link WorkerTask} Task Run ID.
     * </p>
     *
     * @param key the key of the worker job to be deleted.
     */
    void deleteByKey(String key);
}
