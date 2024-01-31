package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.validation.ConstraintViolationException;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContext) throws ConstraintViolationException;

    Trigger save(Trigger trigger) throws ConstraintViolationException;

    Trigger update(Trigger trigger);

    List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext);
}
