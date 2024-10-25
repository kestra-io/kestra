package io.kestra.core.server;

import java.util.Optional;

/**
 * Service interface for updating the state of a service instance.
 *
 * @see ServiceLivenessManager
 * @see AbstractServiceLivenessCoordinator
 */
public interface ServiceLivenessUpdater {

    /**
     * Updates the state of the given service instance with the service liveness coordinator.
     *
     * <p>
     * This method will force synchronisation regardless of the existing state of the service.
     * It must be used by a service when joining a Kestra cluster for the first time or for re-joining
     * having being temporally disconnected.
     *
     * @param service The service to be saved.
     */
    void update(ServiceInstance service);

    /**
     * Attempts to update the state of an existing service to a given new state.
     *
     * <p>
     * This method may not update the service if the transition is not valid.
     *
     * @param instance The service instance.
     * @param newState The new state of the service.
     * @return an optional of the {@link ServiceInstance} or {@link Optional#empty()} if the service is not running.
     */
    default ServiceStateTransition.Response update(final ServiceInstance instance,
                                                   final Service.ServiceState newState) {
        return update(instance, newState, null);
    }

    /**
     * Attempts to update the state of an existing service to a given new state.
     *
     * <p>
     * This method may not update the service if the transition is not valid.
     *
     * @param instance The service instance.
     * @param newState The new state of the service.
     * @param reason   The human-readable reason of the state transition
     * @return an optional of the {@link ServiceInstance} or {@link Optional#empty()} if the service is not running.
     */
    ServiceStateTransition.Response update(final ServiceInstance instance,
                                           final Service.ServiceState newState,
                                           final String reason);
}
