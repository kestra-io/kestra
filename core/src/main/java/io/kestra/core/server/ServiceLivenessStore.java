package io.kestra.core.server;

import java.util.List;
import java.util.Set;

import io.kestra.core.server.Service.ServiceState;

/**
 * Service interface for querying the state of service instances.
 *
 * @see ServiceInstance
 * @see ServiceLivenessUpdater
 * @see DefaultServiceLivenessCoordinator
 */
public interface ServiceLivenessStore {

    /**
     * Finds all service instances that are in one of the given states.
     *
     * @param states the state of services.
     *
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesInStates(Set<ServiceState> states);

    /**
     * Finds all service instances that in the given state.
     *
     * @param state the state of services.
     *
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesInState(ServiceState state);
}
