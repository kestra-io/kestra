package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTriggerRepository implements TriggerRepositoryInterface {

    private final List<Trigger> triggers = new ArrayList<>();

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Trigger> findAllForAllTenants() {
        return this.triggers;
    }

    @Override
    public List<Trigger> findByNextExecutionDateReady(ZonedDateTime now, ScheduleContextInterface scheduleContextInterface) {
        return this.triggers.stream().filter(trigger -> trigger.getNextExecutionDate() == null || trigger.getNextExecutionDate().isBefore(now)).toList();
    }


    @Override
    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) {
        return save(trigger);

    }

    @Override
    public Trigger save(Trigger trigger) {
        triggers.add(trigger);

        return trigger;
    }

    @Override
    public void delete(Trigger trigger) {
        triggers.remove(trigger);
    }

    @Override
    public ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace) {
        throw new UnsupportedOperationException();
    }
}
