package org.kestra.repository.memory;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.repositories.TriggerRepositoryInterface;

import java.util.*;
import javax.inject.Singleton;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTriggerRepository implements TriggerRepositoryInterface {
    private final Map<String, Trigger> triggers = new HashMap<>();

    @Override
    public Optional<Trigger> findLast(TriggerContext context) {
        return triggers.containsKey(context.uid()) ?
            Optional.of(triggers.get(context.uid())) :
            Optional.empty();
    }

    @Override
    public Trigger save(Trigger trigger) {
        triggers.put(trigger.uid(), trigger);

        return trigger;
    }
}
