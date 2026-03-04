package io.kestra.jdbc.runner;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.ScheduleContextInterface;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Singleton
@JdbcRunnerEnabled
public class JdbcSchedulerTriggerState implements SchedulerTriggerStateInterface {
    protected AbstractJdbcTriggerRepository triggerRepository;

    public JdbcSchedulerTriggerState(AbstractJdbcTriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @PostConstruct
    public void initTriggerEvaluateRunning() {
        // trigger evaluateRunning lock can exist when launching the scheduler, we clear it.
        // it's possible since the scheduler on jdbc must be a single node
        this.triggerRepository.findAllForAllTenants().forEach(trigger -> {
            if (trigger.getEvaluateRunningDate() != null) {
                var unlocked = trigger.toBuilder().evaluateRunningDate(null).build();
                this.triggerRepository.save(unlocked);
            }
        });
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext context) {
        return this.triggerRepository.findLast(context);
    }

    @Override
    public List<Trigger> findAllForAllTenants() {
        return this.triggerRepository.findAllForAllTenants();
    }

    @Override
    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) {
        this.triggerRepository.save(trigger, scheduleContextInterface);

        return trigger;
    }

    @Override
    public Trigger create(Trigger trigger, String headerContent) {
        return this.triggerRepository.create(trigger);
    }

    @Override
    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface, String headerContent) {
        this.triggerRepository.save(trigger, scheduleContextInterface);

        return trigger;
    }

    @Override
    public Trigger create(Trigger trigger) {
        return this.triggerRepository.create(trigger);
    }

    @Override
    public Trigger update(Trigger trigger) {
        // here we save a trigger after evaluation, but as during its evaluation it can have been disabled in DB,
        // we need to load it form DB and copy the disabled flag if set
        Optional<Trigger> existing = findLast(trigger);
        Trigger updated = trigger;
        if (existing.isPresent() && existing.get().getDisabled()) {
            updated = trigger.toBuilder().disabled(true).build();
        }

        return this.triggerRepository.update(updated);
    }

    @Override
    public Trigger update(FlowWithSource flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext) {
        return this.triggerRepository.update(flow, abstractTrigger, conditionContext);
    }

    @Override
    public void delete(Trigger trigger) throws QueueException {
        this.triggerRepository.delete(trigger);
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext) {
        return this.triggerRepository.findByNextExecutionDateReadyForAllTenants(now, scheduleContext);
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyButLockedTriggers(ZonedDateTime now) {
        return this.triggerRepository.findByNextExecutionDateReadyButLockedTriggers(now);
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyForGivenFlows(List<FlowWithSource> flows, ZonedDateTime now, ScheduleContextInterface scheduleContext) {
        throw new NotImplementedException();
    }
}
