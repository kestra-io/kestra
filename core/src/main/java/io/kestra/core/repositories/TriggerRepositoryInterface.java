package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Optional<Trigger> findByExecution(Execution execution);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger);

    void delete(Trigger trigger);

    Trigger update(Trigger trigger);

    Trigger lock(String triggerUid, Function<Trigger, Trigger> function);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace, String flowId) throws IllegalArgumentException;

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
}

