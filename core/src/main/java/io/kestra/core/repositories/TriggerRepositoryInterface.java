package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;

import java.util.List;
import java.util.Optional;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Optional<Trigger> findByExecution(Execution execution);

    List<Trigger> findAll();

    Trigger save(Trigger trigger);
}

