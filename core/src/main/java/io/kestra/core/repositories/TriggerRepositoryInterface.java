package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface TriggerRepositoryInterface extends QueryBuilderInterface<Triggers.Fields> {
    Optional<Trigger> findLast(TriggerContext trigger);

    Optional<Trigger> findByUid(String uid);
    
    List<Trigger> findAll(String tenantId);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger);

    void delete(Trigger trigger);

    Trigger update(Trigger trigger);

    Trigger lock(String triggerUid, Function<Trigger, Trigger> function);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId);
    ArrayListTotal<Trigger> find(Pageable from, String tenantId, List<QueryFilter> filters);

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
    Flux<Trigger> findAsync(String tenantId, List<QueryFilter> filters);


    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
}

