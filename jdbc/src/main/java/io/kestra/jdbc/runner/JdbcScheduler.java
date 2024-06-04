package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
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
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.BiConsumer;

@JdbcRunnerEnabled
@Singleton
@Slf4j
@Replaces(DefaultScheduler.class)
public class JdbcScheduler extends AbstractScheduler {
    private final QueueInterface<Execution> executionQueue;
    private final TriggerRepositoryInterface triggerRepository;
    private final ConditionService conditionService;

    private final FlowRepositoryInterface flowRepository;
    private final JooqDSLContextWrapper dslContextWrapper;


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
        dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
    }

    @Override
    public void run() {
        super.run();

        this.receiveCancellations.addFirst(executionQueue.receive(
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize an execution: {}", either.getRight().getMessage());
                    return;
                }

                Execution execution = either.getLeft();
                if (execution.getTrigger() != null) {
                    var flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId()).orElse(null);
                    if (execution.isDeleted() || conditionService.isTerminatedWithListeners(flow, execution)) {
                        // reset scheduler trigger at end
                        triggerRepository
                            .findByExecution(execution)
                            .ifPresent(trigger -> {
                                this.triggerState.update(trigger.resetExecution(execution.getState().getCurrent()));
                            });
                    } else {
                        // update execution state on each state change so the scheduler knows the execution is running
                        triggerRepository
                            .findByExecution(execution)
                            .filter(trigger -> execution.getState().getCurrent() != trigger.getExecutionCurrentState())
                            .ifPresent(trigger -> {
                                ((JdbcSchedulerTriggerState) this.triggerState).updateExecution(Trigger.of(execution, trigger));
                            });
                    }
                }
            }
        ));

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

    @Override
    public void handleNext(List<Flow> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer) {
        JdbcSchedulerContext schedulerContext = new JdbcSchedulerContext(this.dslContextWrapper);

        schedulerContext.startTransaction(scheduleContextInterface -> {
            List<Trigger> triggers = this.triggerState.findByNextExecutionDateReadyForAllTenants(now, scheduleContextInterface);

            consumer.accept(triggers, scheduleContextInterface);
        });
    }
}
