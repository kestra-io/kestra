package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.plugin.core.dashboard.data.Triggers;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

/**
 * Repository interface for searching for trigger states.
 */
public interface TriggerRepositoryInterface extends QueryBuilderInterface<Triggers.Fields> {
    /**
     * Finds the trigger state for the given identifier.
     *
     * @param trigger the identifier.
     * @return an optional {@link TriggerState}.
     */
    Optional<TriggerState> findById(TriggerId trigger);

    /**
     * Finds all trigger states for the given tenant id
     *
     * @param tenantId the tenant identifier - cannot be {@code null}
     * @return the list of trigger states.
     */
    List<TriggerState> findAll(String tenantId);

    /**
     * Finds all trigger states across all tenants.
     *
     * @return the list of trigger states.
     */
    List<TriggerState> findAllForAllTenants();

    /**
     * Searches for all trigger states matching the given criterion.
     *
     * @param from the pageable.
     * @param tenantId the tenant identifier - cannot be {@code null}
     * @return the list of matching trigger states.
     */
    ArrayListTotal<TriggerState> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId);

    /**
     * Searches for all trigger states matching the given tenant and filters.
     *
     * @param from the pageable.
     * @param tenantId the tenant identifier - cannot be {@code null}
     * @param filters the query filters.
     * @return the list of matching trigger states.
     */
    ArrayListTotal<TriggerState> find(Pageable from, String tenantId, List<QueryFilter> filters);

    /**
     * Counts the total number of triggers.
     *
     * @param tenantId the tenant of the triggers
     * @return The count.
     */
    long countAll(@Nullable String tenantId);

    /**
     * Find all triggers that match the query, return a flux of triggers
     */
    Flux<TriggerState> find(String tenantId, List<QueryFilter> filters);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
}
