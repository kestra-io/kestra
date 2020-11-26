package org.kestra.runner.memory;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.schedulers.SchedulerTriggerStateInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;

@Singleton
@MemoryQueueEnabled
public class MemorySchedulerTriggerState implements SchedulerTriggerStateInterface {
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
