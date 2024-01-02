package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.micronaut.data.model.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Optional<Trigger> findByExecution(Execution execution);

    List<Trigger> findAllForAllTenants();

    List<Trigger> findByNextExecutionDateReady(ZonedDateTime now, ScheduleContextInterface scheduleContextInterface);

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface);

    Trigger save(Trigger trigger);

    void delete(Trigger trigger);

    ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return Function.identity();
    }
}

