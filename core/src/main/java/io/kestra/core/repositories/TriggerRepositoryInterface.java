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
     * Returns whether the given trigger is disabled in its persisted {@link TriggerState}.
     * <p>
     * This is the disabled flag the scheduler owns, as opposed to the one the flow source declares. The
     * scheduler enforces it for the triggers it evaluates; a trigger it does not evaluate is only stopped by it
     * where it fires, so its consumer has to check it there. A missing state means the scheduler has not
     * initialized it yet, which is never a disable.
     *
     * @param trigger the identifier.
     */
    default boolean isDisabled(TriggerId trigger) {
        return findById(trigger).map(TriggerState::isDisabled).orElse(false);
    }

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
