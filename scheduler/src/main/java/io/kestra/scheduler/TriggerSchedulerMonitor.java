package io.kestra.scheduler;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.LogService;
import io.kestra.scheduler.model.TriggerState;
import io.kestra.scheduler.stores.TriggerStateStore;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Singleton
public class TriggerSchedulerMonitor implements Runnable {
    
    private static final Logger LOG = LoggerFactory.getLogger(TriggerSchedulerMonitor.class);
    
    private final MetricRegistry metricRegistry;
    private final ExecutionRepositoryInterface executionRepository;
    private final LogService logService;
    private final TriggerStateStore triggerStateStore;
    private final DefaultScheduler defaultScheduler;
    
    @Inject
    public TriggerSchedulerMonitor(MetricRegistry metricRegistry, 
                                   ExecutionRepositoryInterface executionRepository,
                                   TriggerStateStore triggerStateStore,
                                   LogService logService,
                                   DefaultScheduler defaultScheduler) {
        this.metricRegistry = metricRegistry;
        this.executionRepository = executionRepository;
        this.logService = logService;
        this.triggerStateStore = triggerStateStore;
        this.defaultScheduler = defaultScheduler;
    }
    
    @Scheduled(fixedDelay = "PT10S", initialDelay = "PT30S")
    @Override
    public void run() {
        try {
            // Retrieve triggers with non-null execution_id from all corresponding virtual nodes
            ZonedDateTime now = ZonedDateTime.now();
            List<TriggerState> triggers = this.triggerStateStore.findTriggersEligibleForScheduling(now, defaultScheduler.currentVNodesAssignment(), true);
            if (CollectionUtils.isEmpty(triggers)) {
                LOG.debug("No locked triggers. Skip trigger monitoring.");
                return;
            }
            triggers.forEach(state -> {
                Optional<Execution> execution = findExecutionForTrigger(state.getTenantId(), state.getTriggerId());
                // executionState hasn't received the execution, we skip
                if (execution.isEmpty()) {
                    if (state.getUpdatedAt() != null) {
                        metricRegistry
                            .timer(MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION, MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION_DESCRIPTION, metricRegistry.tags(state))
                            .record(Duration.between(state.getUpdatedAt(), Instant.now()));
                    }
                    if (state.getUpdatedAt() == null || state.getUpdatedAt().plusSeconds(60).isBefore(Instant.now())) {
                        logService.logTrigger(
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
                    logService.logTrigger(
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
    
    private Optional<Execution> findExecutionForTrigger(final String tenant, String triggerId) {
        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.UNPAGED, tenant, List.of(QueryFilter
            .builder()
            .field(QueryFilter.Field.TRIGGER_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value(triggerId)
            .build()
        ));
        return executions.isEmpty() ? Optional.empty() : Optional.of(executions.getFirst());
    }
}
