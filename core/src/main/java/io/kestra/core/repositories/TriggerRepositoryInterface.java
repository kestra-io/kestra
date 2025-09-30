package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public interface TriggerRepositoryInterface extends QueryBuilderInterface<Triggers.Fields> {
    Optional<Trigger> findLast(TriggerId trigger);

    Optional<Trigger> findByExecution(Execution execution);

    List<Trigger> findAll(String tenantId);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger);

    void delete(Trigger trigger);

    Trigger update(Trigger trigger);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId);
    ArrayListTotal<Trigger> find(Pageable from, String tenantId, List<QueryFilter> filters);

    /**
     * Counts the total number of triggers.
     *
     * @param tenantId the tenant of the triggers
     * @return The count.
     */
    int count(@Nullable String tenantId);

    /**
     * Find all triggers that match the query, return a flux of triggers
     * as the search is not paginated
     */
    Flux<Trigger> find(String tenantId, List<QueryFilter> filters);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
    
    /**
     * Finds all {@link Trigger} instances that are eligible to be scheduled as of the specified timestamp.
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
    List<Trigger> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked);
}

