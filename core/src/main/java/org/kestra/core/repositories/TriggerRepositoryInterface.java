package org.kestra.core.repositories;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;

import java.util.List;
import java.util.Optional;

public interface TriggerRepositoryInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    List<Trigger> findAll();
}
