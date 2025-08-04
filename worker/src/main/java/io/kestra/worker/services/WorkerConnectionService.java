package io.kestra.worker.services;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Service responsible for establishing the initial connection between a worker and the controller.
 * <p>
 * This service handles worker registration and configuration resolution during startup.
 */
public interface WorkerConnectionService {

    /**
     * Establishes a connection with the controller and resolves worker configuration.
     *
     * @param workerId       the unique identifier of the worker
     * @param workerGroupKey the worker group key from configuration (maybe null)
     * @return the connection result containing a resolved configuration
     * @throws WorkerConnectionFailedException if the connection attempt fails or is not authorized.
     */
    ConnectionResult connect(String workerId, String workerGroupKey) throws WorkerConnectionFailedException;

    /**
     * Result of a worker connection attempt.
     *
     * @param workerGroup the resolved worker group name (may be null if no group is assigned)
     */
    record ConnectionResult(String workerGroup) {
    }

    class WorkerConnectionFailedException extends KestraRuntimeException {
        public WorkerConnectionFailedException(String message) {
            super(message);
        }

        public WorkerConnectionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}