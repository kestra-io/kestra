package org.kestra.core.repositories;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;

import java.util.Optional;
import javax.validation.ConstraintViolationException;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    Trigger save(Trigger trigger) throws ConstraintViolationException;
}
