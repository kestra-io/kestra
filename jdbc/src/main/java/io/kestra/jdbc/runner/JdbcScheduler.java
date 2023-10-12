package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.*;
import io.kestra.core.services.ConditionService;
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
    private final ConditionService conditionService;

    private final FlowRepositoryInterface flowRepository;

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
        conditionService = applicationContext.getBean(ConditionService.class);
        flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);

        this.isReady = true;
    }

    @Override
    public void run() {
        super.run();

        executionQueue.receive(
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    log.error("Unable to dserialize an execution: {}", either.getRight().getMessage());
                    return;
                }

                Execution execution = either.getLeft();
                if (execution.getTrigger() != null) {
                    var flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId()).orElse(null);
                    if (execution.isDeleted() || conditionService.isTerminatedWithListeners(flow, execution)) {
                        // reset scheduler trigger at end
                        triggerRepository
                            .findByExecution(execution)
                            .ifPresent(trigger -> triggerRepository.save(trigger.resetExecution()));
                    } else {
                        // update execution state on each state change so the scheduler knows the execution is running
                        triggerRepository
                            .findByExecution(execution)
                            .filter(trigger -> execution.getState().getCurrent() != trigger.getExecutionCurrentState())
                            .ifPresent(trigger -> triggerRepository.save(Trigger.of(execution, trigger.getDate())));
                    }
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
