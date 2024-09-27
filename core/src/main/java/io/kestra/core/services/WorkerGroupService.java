package io.kestra.core.services;

import io.kestra.core.runners.WorkerJob;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides business logic to manipulate Worker Groups.
 */
@Singleton
@Slf4j
public class WorkerGroupService {

    public boolean isWorkerGroupExistForKey(String key) {
        return true;
    }

    public boolean isWorkerGroupAvailableForKey(String key) {
        return true;
    }

    public String resolveGroupFromKey(String workerGroupKey) {
        // Worker Group is an EE functionality, setting a worker group key when starting the Worker is a no-op.
        return null;
    }

    public String resolveGroupFromJob(WorkerJob workerJob) {
        // Worker Group is an EE functionality, setting a worker group in a task is not possible (validation error),
        // and even if possible it will be a no-op.
        return null;
    }
}
