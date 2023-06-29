package io.kestra.runner.memory;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MemoryQueueEnabled
public class MemorySchedulerTriggerState implements SchedulerTriggerStateInterface {
    private final Map<String, Trigger> triggers = new ConcurrentHashMap<>();

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    QueueInterface<Trigger> triggerQueue;

    @Override
    public Optional<Trigger> findLast(TriggerContext context) {
        return Optional.ofNullable(triggers.get(context.uid()));
    }

    @Override
    public Trigger save(Trigger trigger) {
        triggers.put(trigger.uid(), trigger);
        triggerQueue.emit(trigger);

        return trigger;
    }
}
