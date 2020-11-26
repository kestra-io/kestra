package org.kestra.core.schedulers;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;

import java.util.Optional;
import javax.validation.ConstraintViolationException;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Trigger save(Trigger trigger) throws ConstraintViolationException;
}
