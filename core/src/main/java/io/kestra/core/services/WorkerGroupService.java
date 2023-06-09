package io.kestra.core.services;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides business logic to manipulate Worker Groups.
 */
@Singleton
@Slf4j
public class WorkerGroupService {
    public String resolveGroupFromKey(String workerGroupKey) {
        // Worker Group is an EE functionality, setting a worker group key when starting the Worker is a no-op
        return null;
    }
}
