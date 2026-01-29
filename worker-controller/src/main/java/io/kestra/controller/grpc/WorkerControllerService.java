package io.kestra.controller.grpc;

import io.grpc.BindableService;

/**
 * gRPC service interface for worker-controller communication.
 * <p>
 * This is a marker interface extending {@link BindableService} to define
 * the contract for gRPC services that handle interactions between workers and the controller.
 */
public interface WorkerControllerService extends BindableService {
}
