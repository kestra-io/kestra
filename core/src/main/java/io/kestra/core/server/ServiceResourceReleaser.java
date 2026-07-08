package io.kestra.core.server;

/**
 * This interface defines a contract for releasing resources associated with a service instance.
 * It is called by the {@link io.kestra.executor.DefaultServiceLivenessCoordinator} when a service instance is disconnected or inactive.
 * Multiple implementations of this interface can be registered to handle different types of resources.
 */
public interface ServiceResourceReleaser {
    /**
     * Release resources associated with the given service instance.
     *
     * @param serviceInstance the service instance for which resources are to be released
     * @param reason the reason for releasing the resources
     */
    void releaseResources(ServiceInstance serviceInstance, String reason);
}
