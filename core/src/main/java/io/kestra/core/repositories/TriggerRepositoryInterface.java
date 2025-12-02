package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.kestra.core.scheduler.model.TriggerState;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public interface TriggerRepositoryInterface extends QueryBuilderInterface<Triggers.Fields> {
    Optional<TriggerState> findById(TriggerId trigger);
    
    List<TriggerState> findAll(String tenantId);

    List<TriggerState> findAllForAllTenants();
    
    TriggerState save(TriggerState trigger);

    void delete(TriggerState trigger);
    
    ArrayListTotal<TriggerState> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId);
    
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
    
    /**
     * Finds all {@link TriggerState} instances that are eligible to be scheduled as of the specified timestamp.
     *
     * @param now
     *        the current timestamp used to evaluate scheduling eligibility;
     *        triggers with a next execution time less than or equal to this
     *        value are considered eligible
     * @param vNodes
     *        the set of virtual node identifiers used to restrict the search scope;
     * @param locked
     *        if {@code true}, only locked triggers are returned;
     *        if {@code false}, only unlocked triggers are returned
     * @return a list of triggers that are eligible for scheduling at the given time
     */
    List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked);
    
    /**
     * FOR KESTRA 2.0 MIGRATION
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    List<Trigger> findAllForAllTenantsV1();
}

