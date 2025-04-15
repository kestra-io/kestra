package io.kestra.jdbc.runner;

import com.google.common.collect.Lists;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.*;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.BiConsumer;

@JdbcRunnerEnabled
@Singleton
@Slf4j
public class JdbcScheduler extends AbstractScheduler {
    private final TriggerRepositoryInterface triggerRepository;
    private final JooqDSLContextWrapper dslContextWrapper;
    private final PluginDefaultService pluginDefaultService;

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
        pluginDefaultService = applicationContext.getBean(PluginDefaultService.class);
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
    }

    @Override
    public void handleNext(List<FlowWithSource> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer) {
        JdbcSchedulerContext schedulerContext = new JdbcSchedulerContext(this.dslContextWrapper);

        schedulerContext.doInTransaction(scheduleContextInterface -> {
            Instant startInstant = Instant.now();
            List<Trigger> triggers = this.triggerState.findByNextExecutionDateReadyForAllTenants(now, scheduleContextInterface);
            metricRegistry
                .timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_FIND_BATCH_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_FIND_BATCH_DURATION_DESCRIPTION, Lists.newArrayList().toArray(new String[0]))
                .record(Duration.between(startInstant, Instant.now()));
            consumer.accept(triggers, scheduleContextInterface);
        });
    }
}
