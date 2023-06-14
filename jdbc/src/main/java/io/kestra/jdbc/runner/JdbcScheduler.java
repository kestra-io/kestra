package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.*;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
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
        triggerRepository = applicationContext.getBean(AbstractJdbcTriggerRepository.class);
        triggerState = applicationContext.getBean(SchedulerTriggerStateInterface.class);
        executionState = applicationContext.getBean(SchedulerExecutionState.class);

        this.isReady = true;
    }

    @Override
    public void run() {
        super.run();

        // reset scheduler trigger at end
        executionQueue.receive(
            Scheduler.class,
            execution -> {
                if (
                    execution.getTrigger() != null && (
                        execution.isDeleted() ||
                            execution.getState().getCurrent().isTerminated()
                    )
                ) {
                    triggerRepository
                        .findByExecution(execution)
                        .ifPresent(trigger -> triggerRepository.save(trigger.resetExecution()));
                }
            }
        );

        // remove trigger on flow update
        this.flowListeners.listen((flow, previous) -> {
            if (flow.isDeleted()) {
                ListUtils.emptyOnNull(flow.getTriggers())
                    .forEach(abstractTrigger -> triggerRepository.delete(Trigger.of(flow, abstractTrigger)));
            } else if (previous != null) {
                FlowService
                    .findRemovedTrigger(flow, previous)
                    .forEach(abstractTrigger -> triggerRepository.delete(Trigger.of(flow, abstractTrigger)));
            }
        });
    }
}
