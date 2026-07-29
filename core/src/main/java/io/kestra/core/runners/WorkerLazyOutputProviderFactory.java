package io.kestra.core.runners;

/**
 * Factory to create a {@link LazyOutputProvider} suitable for use on workers.
 * <p>
 * In standalone mode, the default implementation uses direct {@link io.kestra.core.services.TaskOutputService} access.
 * In distributed mode, a gRPC-backed implementation communicates with the controller.
 */
public interface WorkerLazyOutputProviderFactory {

    /**
     * Create a {@link LazyOutputProvider} for the given execution.
     *
     * @param tenantId    the tenant ID
     * @param executionId the execution ID
     */
    LazyOutputProvider create(String tenantId, String executionId);
}
