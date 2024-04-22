package io.kestra.runner.memory;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    public List<Trigger> findAllForAllTenants() {
        return new ArrayList<>(triggers.values());
    }

    @Override
    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) {
        triggers.put(trigger.uid(), trigger);
        triggerQueue.emit(trigger);

        return trigger;
    }

    @Override
    public Trigger create(Trigger trigger) {
        triggers.put(trigger.uid(), trigger);
        triggerQueue.emit(trigger);

        return trigger;
    }

    @Override
    public Trigger update(Trigger trigger) {
        triggers.put(trigger.uid(), trigger);
        triggerQueue.emit(trigger);

        return trigger;
    }

    @Override
    public Trigger update(Flow flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext) throws Exception {
        Optional<Trigger> lastTrigger = this.findLast(Trigger.of(flow, abstractTrigger));
        return this.update(Trigger.of(flow, abstractTrigger, conditionContext, lastTrigger));
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext) {
        return triggers.values().stream().filter(trigger -> trigger.getNextExecutionDate() == null || trigger.getNextExecutionDate().isBefore(now)).toList();
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyForGivenFlows(List<Flow> flows, ZonedDateTime now, ScheduleContextInterface scheduleContext) {
        throw new NotImplementedException();
    }

    @Override
    public void unlock(Trigger trigger) {}
}
