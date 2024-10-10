package io.kestra.core.repositories;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Repository service for storing service instance.
 *
 * @see io.kestra.core.server.ServerInstance
 */
public interface ServiceInstanceRepositoryInterface {

    /**
     * Finds the service instance for the given id.
     *
     * @param id The service's ID. cannot be {@code null}.
     * @return an {@link Optional} of {@link ServiceInstance}, or {@link Optional#empty()}
     */
    Optional<ServiceInstance> findById(String id);

    /**
     * Finds all service instances.
     *
     * @return a list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAll();

    /**
     * Find service instances.
     *
     * @param pageable The {@link Pageable}.
     * @return a list of {@link ServiceInstance}.
     */
    ArrayListTotal<ServiceInstance> find(Pageable pageable,
                                         Set<Service.ServiceState> states,
                                         Set<Service.ServiceType> types);

    /**
     * Deletes the given service instance.
     *
     * @param service The service to be deleted.
     */
    void delete(ServiceInstance service);

    /**
     * Saves the given service instance.
     *
     * @param service The service to be saved.
     * @return The saved instance.
     */
    ServiceInstance save(ServiceInstance service);

    /**
     * Finds all service instances which are in the given state.
     *
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesInState(final Service.ServiceState state);

    /**
     * Finds all service instances which are in the given state.
     *
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesInStates(final Set<Service.ServiceState> states);

    /**
     * Finds all service active instances between the given dates.
     *
     * @param type The service type.
     * @param from The date from (inclusive)
     * @param to   The date to (exclusive)
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesBetween(final Service.ServiceType type,
                                                  final Instant from,
                                                  final Instant to);

    /**
     * Returns the function to be used for mapping column used to sort result.
     *
     * @return the mapping function.
     */
    default Function<String, String> sortMapping() {
        return Function.identity();
    }
}
