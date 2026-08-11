package io.kestra.core.worker.models;

/**
 * A record that encapsulates the context for a worker.
 *
 * @param workerId The unique identifier for the worker.
 * @param workerGroupId The worker group ID used for group resolution.
 * @param workerThreads The number of threads assigned to the worker.
 */
public record WorkerContext(
    String workerId,
    String workerGroupId,
    int workerThreads) {
}
