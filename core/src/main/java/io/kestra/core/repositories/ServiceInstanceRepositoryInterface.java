package io.kestra.core.repositories;

import io.kestra.core.runners.TransactionContext;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.server.ServiceType;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
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
                                         Set<ServiceType> types);

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
     * Finds all service active instances between the given dates.
     *
     * @param type The service type.
     * @param from The date from (inclusive)
     * @param to   The date to (exclusive)
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesBetween(final ServiceType type,
                                                  final Instant from,
                                                  final Instant to);

    /**
     * Finds all service instances which are NOT {@link Service.ServiceState#RUNNING}, then process them using the consumer.
     */
    void processAllNonRunningInstances(BiConsumer<TransactionContext, ServiceInstance> consumer);

    /**
     * Attempt to transition the state of a given service to a given new state.
     * This method may not update the service if the transition is not valid.
     *
     * @param instance the service instance.
     * @param newState the new state of the service.
     * @return an optional of the {@link ServiceInstance} or {@link Optional#empty()} if the service is not running.
     */
    ServiceStateTransition.Response mayTransitServiceTo(final TransactionContext txContext,
                                                        final ServiceInstance instance,
                                                        final Service.ServiceState newState,
                                                        final String reason);

    /**
     * Finds all service instances that are in the states, then process them using the consumer.
     */
    void processInstanceInStates(Set<Service.ServiceState> states, BiConsumer<TransactionContext, ServiceInstance> consumer);
    /**
     * Purge all instances in the EMPTY state older than the until date.
     *
     * @return the number of purged instances
     */
    int purgeEmptyInstances(Instant until);

    /**
     * Returns the function to be used for mapping column used to sort results.
     *
     * @return the mapping function.
     */
    default Function<String, String> sortMapping() {
        return Function.identity();
    }
}
