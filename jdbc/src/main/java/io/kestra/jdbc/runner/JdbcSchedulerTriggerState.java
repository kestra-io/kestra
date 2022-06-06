package io.kestra.jdbc.runner;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@JdbcRunnerEnabled
public class JdbcSchedulerTriggerState implements SchedulerTriggerStateInterface {
    protected TriggerRepositoryInterface triggerRepository;

    public JdbcSchedulerTriggerState(TriggerRepositoryInterface triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext context) {
        return this.triggerRepository.findLast(context);
    }

    @Override
    public Trigger save(Trigger trigger) {
        triggerRepository.save(trigger);

        return trigger;
    }
}
