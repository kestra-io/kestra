package io.kestra.jdbc.runner;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.time.ZonedDateTime;
import java.util.List;

@Singleton
@JdbcRunnerEnabled
public class JdbcSchedulerTriggerState implements SchedulerTriggerStateInterface {
    protected TriggerRepositoryInterface triggerRepository;

    public JdbcSchedulerTriggerState(TriggerRepositoryInterface triggerRepository) {
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
        triggerRepository.save(trigger, scheduleContextInterface);

        return trigger;
    }

    @Override
    public Trigger save(Trigger trigger) {
        triggerRepository.save(trigger);

        return trigger;
    }

    @Override
    public Trigger update(Trigger trigger) {
        triggerRepository.update(trigger);

        return trigger;
    }

    @Override
    public List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext) {
        return this.triggerRepository.findByNextExecutionDateReadyForAllTenants(now, scheduleContext);
    }
}
