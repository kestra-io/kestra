package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;

import java.util.Optional;
import jakarta.validation.ConstraintViolationException;
import java.util.List;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) throws ConstraintViolationException;

    Trigger save(Trigger trigger) throws ConstraintViolationException;
}
