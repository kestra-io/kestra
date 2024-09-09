package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Optional<Trigger> findByExecution(Execution execution);

    List<Trigger> findAll(String tenantId);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger);

    void delete(Trigger trigger);

    Trigger update(Trigger trigger);

    Trigger lock(String triggerUid, Function<Trigger, Trigger> function);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId);

    /**
     * Counts the total number of triggers.
     *
     * @param tenantId the tenant of the triggers
     * @return The count.
     */
    int count(@Nullable String tenantId);

    /**
     * Counts the total number of triggers for the given namespace.
     *
     * @param tenantId  the tenant of the triggers
     * @param namespace the namespace
     * @return The count.
     */
    int countForNamespace(@Nullable String tenantId, @Nullable String namespace);

    /**
     * Find all triggers that match the query, return a flux of triggers
     * as the search is not paginated
     * @param query the query to search for
     * @param tenantId the tenant of the triggers
     * @param namespace the parent namespace of the triggers
     * @return A flux of triggers
     */
    Flux<Trigger> find(String query, String tenantId, String namespace);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
}

