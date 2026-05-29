package io.kestra.worker.services;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Service responsible for establishing the initial connection between a worker and the controller.
 * <p>
 * This service handles worker registration and configuration resolution during startup.
 */
public interface WorkerConnectionService {

    /**
     * Establishes a connection with the controller.
     *
     * @param workerId the unique identifier of the worker
     * @return the connection result
     * @throws WorkerConnectionFailedException if the connection attempt fails or is not authorized.
     */
    ConnectionResult connect(String workerId) throws WorkerConnectionFailedException;

    /**
     * Result of a worker connection attempt.
     *
     * @param workerGroupId the resolved worker group ID; the controller normalizes the
     *                      absent case to {@link io.kestra.core.worker.WorkerGroups#DEFAULT_ID}
     *                      so this value is always set.
     */
    record ConnectionResult(String workerGroupId) {
    }

    class WorkerConnectionFailedException extends KestraRuntimeException {
        private static final long serialVersionUID = 1L;

        public WorkerConnectionFailedException(String message) {
            super(message);
        }

        public WorkerConnectionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
