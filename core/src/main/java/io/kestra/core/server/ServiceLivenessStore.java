package io.kestra.core.server;

import io.kestra.core.server.Service.ServiceState;

import java.util.List;
import java.util.Set;

/**
 * Service interface for querying the state of service instances.
 *
 * @see ServiceInstance
 * @see ServiceLivenessUpdater
 * @see AbstractServiceLivenessCoordinator
 */
public interface ServiceLivenessStore  {

    /**
     * Finds all service instances which are in one of the given states.
     *
     * @param states the state of services.
     *
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesInStates(Set<ServiceState> states);
}
