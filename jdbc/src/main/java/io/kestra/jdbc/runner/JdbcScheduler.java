package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.*;
import io.kestra.core.services.FlowListenersInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@JdbcRunnerEnabled
@Singleton
@Slf4j
@Replaces(DefaultScheduler.class)
public class JdbcScheduler extends AbstractScheduler {
    private final QueueInterface<Execution> executionQueue;
    private final TriggerRepositoryInterface triggerRepository;

    @SuppressWarnings("unchecked")
    @Inject
    public JdbcScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners
    ) {
        super(applicationContext, flowListeners);

        executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
        triggerState = applicationContext.getBean(SchedulerTriggerStateInterface.class);
        executionState = applicationContext.getBean(SchedulerExecutionState.class);

        this.isReady = true;
    }

    @Override
    public void run() {
        flowListeners.run();

        // reset scheduler trigger at end
        executionQueue.receive(
            Scheduler.class,
            execution -> {
                if (
                    execution.getTrigger() != null && (
                        execution.isDeleted() ||
                            execution.getState().getCurrent().isTerninated()
                    )
                ) {
                    triggerRepository
                        .findByExecution(execution)
                        .ifPresent(trigger -> triggerRepository.save(trigger.resetExecution()));
                }
            }
        );

        super.run();
    }
}
