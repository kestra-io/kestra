package io.kestra.core.repositories;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.server.ServiceType;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

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
     * @param filters The list of {@link QueryFilter} to apply.
     * @return a list of {@link ServiceInstance}.
     */
    ArrayListTotal<ServiceInstance> find(Pageable pageable,
        @Nullable List<QueryFilter> filters);

    /**
     * Find service instances.
     *
     * @param pageable The {@link Pageable}.
     * @param states The set of states to filter on.
     * @param types The set of types to filter on.
     * @return a list of {@link ServiceInstance}.
     */
    default ArrayListTotal<ServiceInstance> find(Pageable pageable,
        @Nullable Set<Service.ServiceState> states,
        @Nullable Set<ServiceType> types) {
        List<QueryFilter> filters = new ArrayList<>();
        if (states != null && !states.isEmpty()) {
            filters.add(
                QueryFilter.builder()
                    .field(QueryFilter.Field.STATE)
                    .operation(QueryFilter.Op.IN)
                    .value(states.stream().map(Enum::name).toList())
                    .build()
            );
        }
        if (types != null && !types.isEmpty()) {
            filters.add(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TYPE)
                    .operation(QueryFilter.Op.IN)
                    .value(types.stream().map(Enum::name).toList())
                    .build()
            );
        }
        return find(pageable, filters);
    }

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
     * @param to The date to (exclusive)
     * @return the list of {@link ServiceInstance}.
     */
    List<ServiceInstance> findAllInstancesBetween(final ServiceType type,
        final Instant from,
        final Instant to);

    /**
     * Finds all service instances which are NOT {@link Service.ServiceState#RUNNING}, {@link Service.ServiceState#CREATED} or {@link Service.ServiceState#INACTIVE},
     * then process them using the consumer.
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

    /**
     * Expands a list of state names to include backward-compatible aliases.
     * <p>
     * In Kestra &lt; 1.0 the {@code INACTIVE} state was stored as {@code "EMPTY"}.
     * This method ensures that a filter for {@code INACTIVE} implicitly covers both names,
     * so queries work correctly against mixed-version data.
     *
     * @param stateNames the original state name list
     * @return a new list with {@code "EMPTY"} added when {@code INACTIVE} is present, otherwise the original list
     */
    private static List<String> expandStateNamesForBackwardCompat(List<String> stateNames) {
        if (stateNames.contains(Service.ServiceState.INACTIVE.name()) && !stateNames.contains("EMPTY")) {
            List<String> expanded = new ArrayList<>(stateNames);
            expanded.add("EMPTY");
            return expanded;
        }
        return stateNames;
    }

    /**
     * Rewrites any {@link QueryFilter.Field#STATE} filters in the list to include
     * backward-compatible state name aliases via {@link #expandStateNamesForFilterQueryBackwardCompat(List)}.
     *
     * @param filters the original filter list (may be {@code null})
     * @return a new filter list with STATE filters rewritten, or the original list when no rewrite is needed
     */
    static List<QueryFilter> expandStateNamesForFilterQueryBackwardCompat(List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return filters;
        }
        return filters.stream().map(filter ->
        {
            if (filter.field() != QueryFilter.Field.STATE) {
                return filter;
            }
            List<String> stateNames = switch (filter.value()) {
                case List<?> list -> list.stream().map(Object::toString).toList();
                case String s -> List.of(s);
                default -> throw new io.kestra.core.exceptions.InvalidQueryFiltersException(
                    "STATE requires a String or List value"
                );
            };
            List<String> expanded = expandStateNamesForBackwardCompat(stateNames);
            if (expanded == stateNames) {
                return filter;
            }
            return QueryFilter.builder()
                .field(filter.field())
                .operation(filter.operation())
                .value(expanded)
                .build();
        }).toList();
    }

}
