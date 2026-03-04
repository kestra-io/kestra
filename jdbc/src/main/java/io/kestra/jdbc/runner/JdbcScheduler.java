package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.ScheduleContextInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.scheduler.SchedulerExecutionState;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.BiConsumer;

@JdbcRunnerEnabled
@Singleton
@Slf4j
public class JdbcScheduler extends AbstractScheduler {
    private final TriggerRepositoryInterface triggerRepository;
    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public JdbcScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners
    ) {
        super(applicationContext, flowListeners);

        triggerRepository = applicationContext.getBean(AbstractJdbcTriggerRepository.class);
        triggerState = applicationContext.getBean(SchedulerTriggerStateInterface.class);
        executionState = applicationContext.getBean(SchedulerExecutionState.class);
        dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
    }

    @Override
    public void run() {
        super.run();

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

        // No-op consumption of the trigger queue, so the events are purged from the queue
        this.triggerQueue.receive(Scheduler.class, trigger -> { });
    }

    @Override
    public void handleNext(List<FlowWithSource> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer) {
        JdbcSchedulerContext schedulerContext = new JdbcSchedulerContext(this.dslContextWrapper);

        schedulerContext.doInTransaction(scheduleContextInterface -> {
            List<Trigger> triggers = this.triggerState.findByNextExecutionDateReadyForAllTenants(now, scheduleContextInterface);

            consumer.accept(triggers, scheduleContextInterface);
        });
    }
}
