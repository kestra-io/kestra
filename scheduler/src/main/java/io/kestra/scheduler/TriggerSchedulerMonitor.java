package io.kestra.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.utils.Logs;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "kestra.server-type", pattern = "(SCHEDULER|STANDALONE)")
public class TriggerSchedulerMonitor implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerSchedulerMonitor.class);

    private final MetricRegistry metricRegistry;
    private final ExecutionRepositoryInterface executionRepository;
    private final TriggerStateStore triggerStateStore;
    private final BeanProvider<DefaultScheduler> defaultSchedulerProvider;

    @Inject
    public TriggerSchedulerMonitor(MetricRegistry metricRegistry,
        ExecutionRepositoryInterface executionRepository,
        @Named("cached") TriggerStateStore triggerStateStore,
        BeanProvider<DefaultScheduler> defaultSchedulerProvider) {
        this.metricRegistry = metricRegistry;
        this.executionRepository = executionRepository;
        this.triggerStateStore = triggerStateStore;
        this.defaultSchedulerProvider = defaultSchedulerProvider;
    }

    @Scheduled(fixedDelay = "PT10S", initialDelay = "PT30S")
    @Override
    public void run() {
        try {
            // Resolved lazily so that a DefaultScheduler construction failure (its VNodesAssigner
            // subscribes to the event queue from @PostConstruct, which can fail before a JDBC
            // connection is available) is caught below instead of escalating to Micronaut's
            // scheduled-task executor, which would otherwise retry constructing this bean's entire
            // dependency graph on every tick.
            DefaultScheduler scheduler = defaultSchedulerProvider.get();
            if (!scheduler.getState().isRunning()) {
                LOG.debug("Scheduler is not running (state={}). Skip trigger monitoring.", scheduler.getState());
                return;
            }

            // Retrieve all locked triggers from all corresponding virtual nodes.
            // Realtime triggers stay locked for their entire processing lifetime and are not bound to a
            // single execution lifecycle, so they would otherwise be reported as blocked indefinitely.
            ZonedDateTime now = SchedulerClock.now();
            List<TriggerState> triggers = this.triggerStateStore.findTriggersEligibleForScheduling(now, scheduler.currentVNodesAssignment(), true)
                .stream()
                .filter(state -> !TriggerType.REALTIME.equals(state.getType()))
                .toList();
            if (CollectionUtils.isEmpty(triggers)) {
                LOG.debug("No locked triggers. Skip trigger monitoring.");
                return;
            }
            triggers.forEach(state ->
            {
                Optional<Execution> execution = this.executionRepository.findAllByTrigger(state).next().blockOptional();
                if (execution.isEmpty()) {
                    if (state.getUpdatedAt() != null) {
                        metricRegistry
                            .timer(
                                MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION, MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION_DESCRIPTION, metricRegistry.tags(state)
                            )
                            .record(Duration.between(state.getUpdatedAt(), Instant.now()));
                    }
                    if (state.getUpdatedAt() == null || state.getUpdatedAt().plusSeconds(60).isBefore(Instant.now())) {
                        Logs.logTrigger(
                            state,
                            Level.WARN,
                            "No execution found, schedule is blocked since '{}'",
                            state.getUpdatedAt()
                        );
                    }
                    return;
                }
                if (state.getUpdatedAt() != null) {
                    metricRegistry
                        .timer(MetricRegistry.METRIC_SCHEDULER_EXECUTION_LOCK_DURATION, MetricRegistry.METRIC_SCHEDULER_EXECUTION_LOCK_DURATION_DESCRIPTION, metricRegistry.tags(state))
                        .record(Duration.between(state.getUpdatedAt(), Instant.now()));
                }
                if (LOG.isDebugEnabled()) {
                    Logs.logTrigger(
                        state,
                        Level.DEBUG,
                        "Execution '{}' is still '{}', updated at '{}'",
                        execution.get().getId(),
                        execution.get().getState().getCurrent(),
                        state.getUpdatedAt()
                    );
                }
            });
        } catch (Exception e) {
            LOG.error("Unexpected error while monitoring locked triggers", e);
        }
    }
}
