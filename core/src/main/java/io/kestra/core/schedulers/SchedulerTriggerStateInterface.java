package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;

import java.util.Optional;
import jakarta.validation.ConstraintViolationException;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Trigger save(Trigger trigger) throws ConstraintViolationException;
}
