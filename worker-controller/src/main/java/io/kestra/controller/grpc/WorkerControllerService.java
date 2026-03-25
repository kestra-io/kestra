package io.kestra.controller.grpc;

import java.io.Closeable;

import io.grpc.BindableService;

/**
 * gRPC service interface for worker-controller communication.
 * <p>
 * This is a marker interface extending {@link BindableService} to define
 * the contract for gRPC services that handle interactions between workers and the controller.
 * <p>
 * Implementations must release resources (e.g., active worker streams) when {@link #close()} is called.
 */
public interface WorkerControllerService extends BindableService, Closeable {

    /** {@inheritDoc} */
    @Override
    default void close() {
        // no-op by default
    }
}
