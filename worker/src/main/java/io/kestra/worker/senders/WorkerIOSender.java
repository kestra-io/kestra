package io.kestra.worker.senders;

import io.kestra.core.worker.models.WorkerContext;

/**
 * A WorkerIOSender is responsible for processing outgoing data to the worker.
 * <p>
 * A sender mostly does network operations.
 * 
 * @see GrpcWorkerIOSender
 * @see GrpcWorkerIOSenderFactory
 */
public interface WorkerIOSender extends Runnable {

    /**
     * Initializes the worker I/O sender with the provided {@link WorkerContext}.
     *
     * @param workerContext the context containing information about the worker.
     */
    void init(WorkerContext workerContext);

    /**
     * Stops the worker I/O sender.
     */
    void stop();

}
