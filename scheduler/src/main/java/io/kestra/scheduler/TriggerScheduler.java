package io.kestra.scheduler;

import com.google.common.base.Throwables;
import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.LabelService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Logs;
import io.kestra.scheduler.internals.DefaultSchedulableTriggerFetcher;
import io.kestra.scheduler.internals.NextEvaluationDate;
import io.kestra.scheduler.internals.SchedulableEvaluator;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.scheduler.models.TriggerEvaluationContext;
import io.kestra.scheduler.pubsub.TriggerExecutionPublisher;
import io.kestra.scheduler.pubsub.TriggerWorkerJobPublisher;
import io.kestra.scheduler.stores.FlowMetaStore;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service responsible for evaluating and scheduling triggers.
 */
@Singleton
public class TriggerScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);
    
    // Config
    private final SchedulerConfiguration schedulerConfiguration;
    
    // Services
    private final RunContextFactory runContextFactory;
    private final ConditionService conditionService;
    private final TriggerWorkerJobPublisher triggerWorkerJobPublisher;
    private final SchedulableEvaluator schedulableEvaluator;
    private final TriggerExecutionPublisher triggerExecutionSender;
    private final PluginDefaultService pluginDefaultService;
    private final DefaultSchedulableTriggerFetcher schedulableTriggerFetcher;
    
    // Stores
    private final TriggerStateStore triggerStateStore;
    private final FlowMetaStore flowMetaStore;
    
    // Metrics
    private final MetricRegistry metricRegistry;
    private final Counter metricScheduleLoopCounter;
    private final Counter metricEvaluatedTriggerCounter;
    private final Timer metricEvaluationLoopDuration;
    
    @Inject
    public TriggerScheduler(TriggerStateStore triggerStateStore,
                            FlowMetaStore flowMetaStore,
                            MetricRegistry metricRegistry,
                            RunContextFactory runContextFactory,
                            ConditionService conditionService,
                            PluginDefaultService pluginDefaultService,
                            SchedulableEvaluator schedulableEvaluator,
                            DefaultSchedulableTriggerFetcher schedulableTriggerFetcher,
                            TriggerWorkerJobPublisher triggerWorkerJobPublisher,
                            TriggerExecutionPublisher triggerExecutionPublisher,
                            SchedulerConfiguration schedulerConfiguration) {
        this.triggerStateStore = triggerStateStore;
        this.flowMetaStore = flowMetaStore;
        this.runContextFactory = runContextFactory;
        this.pluginDefaultService = pluginDefaultService;
        this.metricRegistry = metricRegistry;
        this.conditionService = conditionService;
        this.triggerWorkerJobPublisher = triggerWorkerJobPublisher;
        this.schedulableEvaluator = schedulableEvaluator;
        this.triggerExecutionSender = triggerExecutionPublisher;
        this.schedulerConfiguration = schedulerConfiguration;
        this.schedulableTriggerFetcher = schedulableTriggerFetcher;
        
        // Metrics
        metricScheduleLoopCounter = metricRegistry
            .counter(MetricRegistry.METRIC_SCHEDULER_LOOP_COUNT, MetricRegistry.METRIC_SCHEDULER_LOOP_COUNT_DESCRIPTION);
        
        metricEvaluatedTriggerCounter = metricRegistry
            .counter(MetricRegistry.METRIC_SCHEDULER_EVALUATE_COUNT, MetricRegistry.METRIC_SCHEDULER_EVALUATE_COUNT_DESCRIPTION);
        
        metricEvaluationLoopDuration = metricRegistry
            .timer(MetricRegistry.METRIC_SCHEDULER_EVALUATION_LOOP_DURATION, MetricRegistry.METRIC_SCHEDULER_EVALUATION_LOOP_DURATION_DESCRIPTION);
    }
    
    
    /**
     * Initializes all triggers for the given set of virtual nodes (vNodes).
     * <p>
     * This method is expected to be invoked by a {@link TriggerSchedulingLoop} on any vNodes assignment changes.
     * </p>
     *
     * @param clock             the scheduler's clock, used to obtain the current time reference and perform time-based evaluations.
     * @param scheduledTime     the target time for which triggers should be evaluated and potentially scheduled; 
     *                          represents the scheduler’s current cycle timestamp.
     * @param vNodesAssignments the set of virtual node identifiers whose associated triggers should be initialized.
     */
    public void onStart(final Clock clock, final Instant scheduledTime, final Set<Integer> vNodesAssignments) {
        log.info("Starting trigger scheduling for {} vNodes", vNodesAssignments);
        
        Map<String, TriggerState> triggers = triggerStateStore.findAllForVNodes(vNodesAssignments).stream()
            .collect(Collectors.toMap(TriggerId::uid, Function.identity(), (existing, replacement) -> {
                // duplicate keys could only happen in unit-tests
                log.warn("Detected duplicate keys for triggers: {}", TriggerId.of(replacement));
                return existing;
            }));
        
        flowMetaStore.findAllForVNodes(vNodesAssignments)
            .stream()
            .map(flow -> pluginDefaultService.injectAllDefaults(flow, log))
            .filter(Objects::nonNull)
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .flatMap(flow -> flow.getTriggers().stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .map(trigger -> Pair.of(flow, trigger)))
            .distinct()
            .forEach(flowAndTrigger -> {
                final FlowWithSource flow = flowAndTrigger.getLeft();
                final AbstractTrigger trigger = flowAndTrigger.getRight();
                
                // Compute trigger vNode
                int vNode = VNodes.computeVNodeFromFlow(flow, schedulerConfiguration.vnodes());
                    
                // Check whether a state already exist for this trigger
                TriggerState triggerState = triggers.get(TriggerId.of(flow, trigger).uid());

                if (triggerState == null) {
                    RunContext runContext = runContextFactory.of(flow, trigger);
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                    try {

                        // Create a TriggerState
                        TriggerState newTriggerState = TriggerState.of(flow, trigger, vNode);

                        if (trigger instanceof Schedulable schedulableTrigger) {
                            ZonedDateTime nextEvaluationDate = schedulableTrigger.nextEvaluationDate(conditionContext, Optional.empty());
                            // schedule are evaluated at the next cron schedule
                            newTriggerState = newTriggerState.updateForNextEvaluationDate(clock, nextEvaluationDate);
                        } else if (trigger instanceof WorkerTriggerInterface) {
                            // worker triggers are evaluated immediately
                            newTriggerState = newTriggerState.updateForNextEvaluationDate(clock, ZonedDateTime.now(clock));
                        }
                        
                        triggerStateStore.save(newTriggerState);
                        Logs.logTrigger(newTriggerState, log, Level.INFO, "New state initialized");
                        
                    } catch (Exception e) {
                        logError(clock, conditionContext, flow, trigger.getId(), e);
                    }
                } else if (trigger instanceof Schedulable schedulableTrigger) {
                    // we recompute the Schedule nextExecutionDate if needed
                    RunContext runContext = runContextFactory.of(flow, trigger);
                    
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                    RecoverMissedSchedules recoverMissedSchedules = Optional.ofNullable(schedulableTrigger.getRecoverMissedSchedules()).orElseGet(() -> schedulableTrigger.defaultRecoverMissedSchedules(runContext));
                    try {
                        TriggerState currentTriggerState = triggerState;
                        switch (recoverMissedSchedules) {
                            case LAST -> {
                                ZonedDateTime previousDate = schedulableTrigger.previousEvaluationDate(conditionContext);
                                if (previousDate.toInstant().isAfter(currentTriggerState.getEvaluatedAt())) {
                                    currentTriggerState = currentTriggerState.updateForNextEvaluationDate(clock, previousDate);
                                    triggerStateStore.save(currentTriggerState);
                                }
                            }
                            case NONE -> {
                                ZonedDateTime nextEvaluationDate = schedulableTrigger.nextEvaluationDate();
                                if (!Objects.equals(currentTriggerState.getNextEvaluationDate(), nextEvaluationDate.toInstant())) {
                                    currentTriggerState = currentTriggerState.updateForNextEvaluationDate(clock, nextEvaluationDate);
                                    triggerStateStore.save(currentTriggerState);
                                }
                            }
                            case ALL -> {
                                // nothing to do
                            }
                        }
                    } catch (Exception e) {
                        logError(clock, conditionContext, flow, trigger.getId(), e);
                    }
                }
            });
    }
    
    /**
     * Evaluates and schedules all triggers that are due to be executed at or before the specified {@code scheduledTime}
     * for the given set of virtual nodes (vNodes).
     * <p>
     * This method is expected to be invoked periodically by a {@link TriggerSchedulingLoop} to ensure that all time-based
     * triggers are processed in a timely manner. 
     * </p>
     *
     * @param clock             the scheduler's clock, used to obtain the current time reference and perform time-based evaluations.
     * @param scheduledTime     the target time for which triggers should be evaluated and potentially scheduled; 
     *                          represents the scheduler’s current cycle timestamp.
     * @param vNodesAssignments the set of virtual node identifiers whose associated triggers should be evaluated.
     */
    public void onSchedule(final Clock clock, final Instant scheduledTime, final Set<Integer> vNodesAssignments) {
        metricScheduleLoopCounter.increment();
        
        ZonedDateTime zoneScheduleTime = ZonedDateTime.ofInstant(scheduledTime, clock.getZone());
        
        // Get schedulable triggers
        List<TriggerEvaluationContext> schedulableTriggers = schedulableTriggerFetcher.getSchedulableTriggers(clock, zoneScheduleTime, vNodesAssignments);
        
        if (log.isTraceEnabled()) {
            log.trace("Found {} schedulable triggers at {}", schedulableTriggers.size(), scheduledTime);
        }
        
        metricEvaluatedTriggerCounter.increment(schedulableTriggers.size());
        
        // Process Triggers
        schedulableTriggers.forEach(triggerEvaluationContext -> evaluate(clock, zoneScheduleTime, triggerEvaluationContext));
        
        // Record metrics
        metricEvaluationLoopDuration.record(Duration.between(scheduledTime, clock.instant()));
    }
    
    /**
     * Evaluates the given trigger context.
     *
     * @param clock         the scheduler's clock.
     * @param scheduledTime the scheduler’s current cycle timestamp.
     * @param context       the {@link TriggerEvaluationContext}.
     */
    private void evaluate(Clock clock, ZonedDateTime scheduledTime, TriggerEvaluationContext context) {
        
        final AbstractTrigger trigger = context.trigger();
        final Logger logger = context.conditionContext().getRunContext().logger();
        
        TriggerState triggerState = context.triggerState();
        triggerState = triggerState.evaluatedAt(clock, triggerState.getNextEvaluationDate());
        
        try {
            List<Condition> conditions = trigger.getConditions() != null ? trigger.getConditions() : List.of();
            
            if (!conditionService.areValid(conditions, context.conditionContext())) {
                updateNextEvaluationDateAndGetOnSuccess(clock, triggerState, context).ifPresent(triggerStateStore::save);
                return;
            }
            
            switch (trigger) {
                case Schedulable schedulableTrigger ->
                    processSchedulableTrigger(clock, scheduledTime, context, triggerState, schedulableTrigger);
                case PollingTriggerInterface pollingTrigger ->
                    processPollingTrigger(clock, triggerState, context, pollingTrigger);
                case RealtimeTriggerInterface realtimeTrigger ->
                    processWorkerTrigger(clock, triggerState, context, realtimeTrigger);
                default -> {
                    logger.error("Unable to evaluate trigger '{}'. Cause: Unsupported type '{}'", trigger.getId(), trigger.getClass().getName());
                }
            }
        } catch (Exception e) {
            logger.error("Unable to evaluate trigger '{}'", trigger.getId(), e);
            
            // Save the final trigger state
            triggerState = triggerState
                .updateForNextEvaluationDate(clock, NextEvaluationDate.get(clock, trigger))
                .updateForExecutionState(clock, State.Type.FAILED)
                .locked(clock, false);
            triggerStateStore.save(triggerState);
            
            // Send the FAILED execution 
            final TriggerContext triggerContext = triggerState.context();
            final FlowInterface flow = context.flow();
            
            Execution execution = Execution.builder()
                .id(IdUtils.create())
                .tenantId(triggerContext.getTenantId())
                .namespace(triggerContext.getNamespace())
                .flowId(triggerContext.getFlowId())
                .flowRevision(flow.getRevision())
                .labels(LabelService.labelsExcludingSystem(flow))
                .state(new State().withState(State.Type.FAILED))
                .build()
                .withScheduleDate(scheduledTime.toInstant())
                .withTenantId(triggerState.getTenantId());
            
            triggerExecutionSender.send(execution);
        }
    }
    
    private void processSchedulableTrigger(Clock clock, ZonedDateTime scheduleTime, TriggerEvaluationContext triggerEvaluationContext, TriggerState triggerState, Schedulable trigger) {
        // Compute and update next evaluation date
        TriggerContext triggerContext = triggerState.context();
        ZonedDateTime nextEvaluationDate = trigger.nextEvaluationDate(triggerEvaluationContext.conditionContext(), Optional.of(triggerContext));
        triggerState = triggerState.updateForNextEvaluationDate(clock, nextEvaluationDate);
        
        // Evaluate Schedulable
        Optional<Execution> maybeExecution = schedulableEvaluator.evaluate(trigger, triggerContext, triggerEvaluationContext.conditionContext());
        if (maybeExecution.isPresent()) {
            log(clock, triggerContext, maybeExecution.get());
            triggerState = triggerState
                .updateForExecution(clock, maybeExecution.get())
                .locked(clock, !((AbstractTrigger)trigger).isAllowConcurrent());
        }
        // Save the final trigger state
        triggerStateStore.save(triggerState);
        
        // May send a new execution - if Schedulable trigger or on error
        final String tenantId = triggerState.getTenantId();
        maybeExecution.ifPresent(execution -> {
            execution = execution
                .withScheduleDate(scheduleTime.toInstant())
                .withTenantId(tenantId);
            triggerExecutionSender.send(execution);
        });
    }
    
    private void processPollingTrigger(Clock clock, TriggerState triggerState, TriggerEvaluationContext triggerEvaluationContext, PollingTriggerInterface trigger) {
        if (trigger.getInterval() == null) {
            Logs.logTrigger(
                triggerState,
                triggerEvaluationContext.conditionContext().getRunContext().logger(),
                Level.ERROR,
                "Trigger must have a non-empty 'interval' configuration."
            );
            return;
        }
        processWorkerTrigger(clock, triggerState, triggerEvaluationContext, trigger);
    }
    
    private void processWorkerTrigger(Clock clock, TriggerState triggerState, TriggerEvaluationContext triggerEvaluationContext, WorkerTriggerInterface trigger) {
        final boolean mustBeLocked = trigger instanceof RealtimeTriggerInterface || !((AbstractTrigger)trigger).isAllowConcurrent();
        updateNextEvaluationDateAndGetOnSuccess(clock, triggerState, triggerEvaluationContext).ifPresent(state -> {
            try {
                this.triggerWorkerJobPublisher.send(state, triggerEvaluationContext.trigger(), triggerEvaluationContext.flow(), triggerEvaluationContext.conditionContext());
                state = state.locked(clock, mustBeLocked);
                triggerStateStore.save(state);
            } catch (Exception e) {
                Logs.logTrigger(
                    triggerState,
                    triggerEvaluationContext.conditionContext().getRunContext().logger(),
                    Level.ERROR,
                    "Unable to send worker trigger to worker",
                    e
                );
            }
        });
    }
    
    private Optional<TriggerState> updateNextEvaluationDateAndGetOnSuccess(Clock clock, TriggerState currentTriggerState, TriggerEvaluationContext lastTriggerEvaluationContext) {
        Logger logger = lastTriggerEvaluationContext.conditionContext().getRunContext().logger();
        try {
            ZonedDateTime nextEvaluationDate = NextEvaluationDate.get(clock, lastTriggerEvaluationContext.trigger(), lastTriggerEvaluationContext.triggerState().context(), lastTriggerEvaluationContext.conditionContext());
           return Optional.of(currentTriggerState.updateForNextEvaluationDate(clock, nextEvaluationDate));
        } catch (Exception e) {
            if (e instanceof InvalidTriggerConfigurationException) {
                // disable trigger on invalid configuration
                triggerStateStore.save(currentTriggerState.disabled(clock, true)); 
            }
            Logs.logTrigger(
                lastTriggerEvaluationContext.triggerState(),
                logger,
                Level.WARN,
                "[date: {}] Evaluation failed. Cause: '{}'",
                lastTriggerEvaluationContext.triggerState().getEvaluatedAt(),
                e.getMessage(),
                e
            );
            if (logger.isTraceEnabled()) {
                logger.trace(Throwables.getStackTraceAsString(e));
            }
        }
        return Optional.empty();
    }
    
    private void log(Clock clock, TriggerContext triggerContext, Execution execution) {
        metricRegistry
            .counter(MetricRegistry.METRIC_SCHEDULER_TRIGGER_COUNT, MetricRegistry.METRIC_SCHEDULER_TRIGGER_COUNT_DESCRIPTION, metricRegistry.tags(execution))
            .increment();
        
        ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        
        if (execution.getTrigger() != null &&
            execution.getTrigger().getVariables() != null &&
            execution.getTrigger().getVariables().containsKey("next")
        ) {
            Object nextVariable = execution.getTrigger().getVariables().get("next");
            
            ZonedDateTime next = (nextVariable != null) ? ZonedDateTime.parse((CharSequence) nextVariable) : null;
            
            // Exclude backfills
            // FIXME : "late" are not excluded and can increase delay value (false positive)
            if (next != null && now.isBefore(next)) {
                metricRegistry
                    .timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_DELAY_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_DELAY_DURATION_DESCRIPTION, metricRegistry.tags(execution))
                    .record(Duration.between(triggerContext.getDate(), now));
            }
        }
        
        Logs.logTrigger(
            triggerContext,
            Level.INFO,
            "Scheduled execution {} at '{}' started at '{}'",
            execution.getId(),
            triggerContext.getDate(),
            now
        );
    }
    
    private void logError(Clock clock, ConditionContext conditionContext, FlowId flow, String triggerId, Throwable e) {
        Logger logger = conditionContext.getRunContext().logger();

        Logs.logExecution(
            flow,
            logger,
            Level.ERROR,
            "[trigger: {}] [date: {}] Evaluate Failed with error '{}'",
            triggerId,
            clock,
            e.getMessage(),
            e
        );
    }
}
