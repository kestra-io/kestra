package io.kestra.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.triggers.*;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.*;
import io.kestra.core.utils.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@SuppressWarnings("this-escape")
public abstract class AbstractScheduler implements Scheduler {
    protected final ApplicationContext applicationContext;
    protected final QueueInterface<Execution> executionQueue;
    protected final QueueInterface<Trigger> triggerQueue;
    private final QueueInterface<WorkerJob> workerJobQueue;
    private final QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;
    private final QueueInterface<ExecutionKilled> executionKilledQueue;
    private final QueueInterface<LogEntry> logQueue;
    @SuppressWarnings("rawtypes")
    private final Optional<QueueInterface> clusterEventQueue;
    protected final FlowListenersInterface flowListeners;
    private final RunContextFactory runContextFactory;
    private final RunContextInitializer runContextInitializer;
    private final MetricRegistry metricRegistry;
    private final ConditionService conditionService;
    private final PluginDefaultService pluginDefaultService;
    private final WorkerGroupService workerGroupService;
    protected SchedulerExecutionStateInterface executionState;
    private final WorkerGroupExecutorInterface workerGroupExecutorInterface;
    private final MaintenanceService maintenanceService;

    // must be volatile as it's updated by the flow listener thread and read by the scheduleExecutor thread
    private volatile Boolean isReady = false;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final ScheduledExecutorService executionMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> executionMonitorFuture;

    @Getter
    protected SchedulerTriggerStateInterface triggerState;

    // schedulable and schedulableNextDate must be volatile and their access synchronized as they are updated and read by different threads.
    @Getter
    private volatile List<FlowWithTriggers> schedulable = new ArrayList<>();
    @Getter
    private volatile Map<String, FlowWithWorkerTriggerNextDate> schedulableNextDate = new ConcurrentHashMap<>();

    private final String id = IdUtils.create();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final ApplicationEventPublisher<ServiceStateChangeEvent> serviceStateEventPublisher;
    protected final ApplicationEventPublisher<CrudEvent<Execution>> executionEventPublisher;
    protected final List<Runnable> receiveCancellations = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @Inject
    public AbstractScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners
    ) {
        this.applicationContext = applicationContext;
        this.executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.workerJobQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED));
        this.executionKilledQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED));
        this.workerTriggerResultQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED));
        this.clusterEventQueue = applicationContext.findBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.CLUSTER_EVENT_NAMED));
        this.logQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED));
        this.flowListeners = flowListeners;
        this.runContextFactory = applicationContext.getBean(RunContextFactory.class);
        this.runContextInitializer = applicationContext.getBean(RunContextInitializer.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.pluginDefaultService = applicationContext.getBean(PluginDefaultService.class);
        this.workerGroupService = applicationContext.getBean(WorkerGroupService.class);
        this.serviceStateEventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.executionEventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.workerGroupExecutorInterface = applicationContext.getBean(WorkerGroupExecutorInterface.class);
        this.maintenanceService = applicationContext.getBean(MaintenanceService.class);

        setState(ServiceState.CREATED);
    }

    @VisibleForTesting
    public boolean isReady() {
        return isReady;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        this.flowListeners.run();
        this.flowListeners.listen(this::initializedTriggers);

        scheduledFuture = scheduleExecutor.scheduleAtFixedRate(
            this::handle,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exception on the evaluation loop thread
        Thread.ofVirtual().name("scheduler-evaluation-loop-watch").start(
            () -> {
                Await.until(scheduledFuture::isDone);

                try {
                    scheduledFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    log.error("Scheduler fatal exception", e);
                    close();
                    applicationContext.close();
                }
            }
        );

        // Periodically report metrics and logs of running executions
        executionMonitorFuture = executionMonitorExecutor.scheduleWithFixedDelay(
            this::executionMonitor,
            30,
            10,
            TimeUnit.SECONDS
        );

        // look at exception on the monitoring loop thread
        Thread.ofVirtual().name("scheduler-monitoring-loop-watch").start(
            () -> {
                Await.until(executionMonitorFuture::isDone);

                try {
                    executionMonitorFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    log.error("Scheduler fatal exception", e);
                    close();
                    applicationContext.close();
                }
            }
        );

        // remove trigger on flow update, update local triggers store, and stop the trigger on the worker
        this.flowListeners.listen((flow, previous) -> {

            if (flow.isDeleted() || previous != null) {
                List<AbstractTrigger> triggersDeleted = flow.isDeleted() ?
                    ListUtils.emptyOnNull(flow.getTriggers()) :
                    FlowService.findRemovedTrigger(flow, previous);

                triggersDeleted.forEach(abstractTrigger -> {
                    Trigger trigger = Trigger.of(flow, abstractTrigger);

                    try {
                        this.triggerQueue.delete(trigger);

                        this.executionKilledQueue.emit(ExecutionKilledTrigger
                            .builder()
                            .tenantId(trigger.getTenantId())
                            .namespace(trigger.getNamespace())
                            .flowId(trigger.getFlowId())
                            .triggerId(trigger.getTriggerId())
                            .build()
                        );
                    } catch (QueueException e) {
                        log.error("Unable to kill the trigger {}.{}.{}", trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId(), e);
                    }
                });

            }

            if (previous != null) {
                FlowService.findUpdatedTrigger(flow, previous)
                    .forEach(abstractTrigger -> {
                        if (abstractTrigger instanceof WorkerTriggerInterface) {
                            RunContext runContext = runContextFactory.of(flow, abstractTrigger);
                            ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);

                            try {
                                this.triggerState.update(flow, abstractTrigger, conditionContext);
                            } catch (Exception e) {
                                logError(conditionContext, flow, abstractTrigger, e);
                            }

                            Trigger trigger = Trigger.of(flow, abstractTrigger);
                            try {
                                this.executionKilledQueue.emit(ExecutionKilledTrigger
                                    .builder()
                                    .tenantId(trigger.getTenantId())
                                    .namespace(trigger.getNamespace())
                                    .flowId(trigger.getFlowId())
                                    .triggerId(trigger.getTriggerId())
                                    .build()
                                );
                            } catch (QueueException e) {
                                log.error("Unable to kill the trigger {}.{}.{}", trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId(), e);
                            }
                        }
                    });
            }
        });

        // listen to WorkerTriggerResult from worker triggers
        this.receiveCancellations.add(this.workerTriggerResultQueue.receive(
            null,
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker trigger result: {}", either.getRight().getMessage());

                    return;
                }

                WorkerTriggerResult workerTriggerResult = either.getLeft();
                if (workerTriggerResult.getTrigger() instanceof RealtimeTriggerInterface && workerTriggerResult.getExecution().isPresent()) {
                    this.emitExecution(workerTriggerResult.getExecution().get(), workerTriggerResult.getTriggerContext());
                } else if (workerTriggerResult.getExecution().isPresent()) {
                    SchedulerExecutionWithTrigger triggerExecution = new SchedulerExecutionWithTrigger(
                        workerTriggerResult.getExecution().get(),
                        workerTriggerResult.getTriggerContext()
                    );
                    ZonedDateTime nextExecutionDate;
                    try {
                        nextExecutionDate = this.nextEvaluationDate(workerTriggerResult.getTrigger());
                    } catch (InvalidTriggerConfigurationException e) {
                        disableInvalidTrigger(workerTriggerResult.getTriggerContext(), e);
                        return;
                    }
                    this.handleEvaluateWorkerTriggerResult(triggerExecution, nextExecutionDate, workerTriggerResult.getTrigger());
                } else {
                    ZonedDateTime nextExecutionDate;
                    try {
                        nextExecutionDate = this.nextEvaluationDate(workerTriggerResult.getTrigger());
                    } catch (InvalidTriggerConfigurationException e) {
                        disableInvalidTrigger(workerTriggerResult.getTriggerContext(), e);
                        return;
                    }
                    this.triggerState.update(Trigger.of(workerTriggerResult.getTriggerContext(), nextExecutionDate));
                }
            }
        ));

        // listen to cluster events
        this.clusterEventQueue.ifPresent(clusterEventQueueInterface -> this.receiveCancellations.addFirst(((QueueInterface<ClusterEvent>) clusterEventQueueInterface).receive(this::clusterEventQueue)));
        if (this.maintenanceService.isInMaintenanceMode()) {
            enterMaintenance();
        } else {
            setState(ServiceState.RUNNING);
        }
        log.info("Scheduler started");
    }

    // Initialized local trigger state,
    // and if some flows were created outside the box, for example from the CLI,
    // then we may have some triggers that are not created yet.
    /* FIXME: There is a race between Kafka stream consumption & initializedTriggers: we can override a trigger update coming from a stream consumption with an old one because stream consumption is not waiting for trigger initialization
    *   Example: we see a SUCCESS execution so we reset the trigger's executionId but then the initializedTriggers resubmits an old trigger state for some reasons (evaluationDate for eg.) */
    private void initializedTriggers(List<FlowWithSource> flows) {
        record FlowAndTrigger(FlowWithSource flow, AbstractTrigger trigger) {
            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) return false;
                FlowAndTrigger that = (FlowAndTrigger) o;
                return Objects.equals(Trigger.uid(this.flow(), this.trigger()), Trigger.uid(that.flow(), that.trigger()));
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(Trigger.uid(this.flow(), this.trigger()));
            }
        }

        synchronized (this) { // we need a sync block as we read then update so we should not do it in multiple threads concurrently
            Map<String, Trigger> triggers = triggerState.findAllForAllTenants().stream().collect(Collectors.toMap(HasUID::uid, Function.identity()));

            flows
                .stream()
                .map(flow -> pluginDefaultService.injectAllDefaults(flow, log))
                .filter(Objects::nonNull)
                .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
                .flatMap(flow -> flow.getTriggers().stream().filter(trigger -> trigger instanceof WorkerTriggerInterface).map(trigger -> new FlowAndTrigger(flow, trigger)))
                .distinct()
                .forEach(flowAndTrigger -> {
                    String triggerUid = Trigger.uid(flowAndTrigger.flow(), flowAndTrigger.trigger());
                    Optional<Trigger> trigger = Optional.ofNullable(triggers.get(triggerUid));
                    if (trigger.isEmpty()) {
                        RunContext runContext = runContextFactory.of(flowAndTrigger.flow(), flowAndTrigger.trigger());
                        ConditionContext conditionContext = conditionService.conditionContext(runContext, flowAndTrigger.flow(), null);
                        try {
                            // new worker triggers will be evaluated immediately except schedule that will be evaluated at the next cron schedule
                            ZonedDateTime nextExecutionDate = flowAndTrigger.trigger() instanceof Schedulable schedule ? schedule.nextEvaluationDate(conditionContext, Optional.empty()) : now();
                            Trigger newTrigger = Trigger.builder()
                                .tenantId(flowAndTrigger.flow().getTenantId())
                                .namespace(flowAndTrigger.flow().getNamespace())
                                .flowId(flowAndTrigger.flow().getId())
                                .triggerId(flowAndTrigger.trigger().getId())
                                .date(now())
                                .nextExecutionDate(nextExecutionDate)
                                .stopAfter(flowAndTrigger.trigger().getStopAfter())
                                .build();

                            // Used for schedulableNextDate
                            FlowWithWorkerTrigger flowWithWorkerTrigger = FlowWithWorkerTrigger.builder()
                                .flow(flowAndTrigger.flow())
                                .abstractTrigger(flowAndTrigger.trigger())
                                .conditionContext(conditionContext)
                                .triggerContext(newTrigger)
                                .build();
                            schedulableNextDate.put(newTrigger.uid(), FlowWithWorkerTriggerNextDate.of(flowWithWorkerTrigger));
                            this.triggerState.create(newTrigger);
                        } catch (Exception e) {
                            logError(conditionContext, flowAndTrigger.flow(), flowAndTrigger.trigger(), e);
                        }
                    } else if (flowAndTrigger.trigger() instanceof Schedulable schedule) {
                        // we recompute the Schedule nextExecutionDate if needed
                        RunContext runContext = runContextFactory.of(flowAndTrigger.flow(), flowAndTrigger.trigger());
                        ConditionContext conditionContext = conditionService.conditionContext(runContext, flowAndTrigger.flow(), null);
                        RecoverMissedSchedules recoverMissedSchedules = Optional.ofNullable(schedule.getRecoverMissedSchedules()).orElseGet(() -> schedule.defaultRecoverMissedSchedules(runContext));
                        try {
                            Trigger lastUpdate = trigger.get();
                            if (recoverMissedSchedules == RecoverMissedSchedules.LAST) {
                                ZonedDateTime previousDate = schedule.previousEvaluationDate(conditionContext);
                                if (previousDate.isAfter(trigger.get().getDate())) {
                                    lastUpdate = trigger.get().toBuilder().nextExecutionDate(previousDate).build();

                                    this.triggerState.update(lastUpdate);
                                }
                            } else {
                                ZonedDateTime nextEvaluationDate = schedule.nextEvaluationDate();
                                if (recoverMissedSchedules == RecoverMissedSchedules.NONE && !Objects.equals(trigger.get().getNextExecutionDate(), nextEvaluationDate)) {
                                    lastUpdate = trigger.get().toBuilder().nextExecutionDate(nextEvaluationDate).build();

                                    this.triggerState.update(lastUpdate);
                                }
                            }
                            // Used for schedulableNextDate
                            FlowWithWorkerTrigger flowWithWorkerTrigger = FlowWithWorkerTrigger.builder()
                                .flow(flowAndTrigger.flow())
                                .abstractTrigger(flowAndTrigger.trigger())
                                .conditionContext(conditionContext)
                                .triggerContext(lastUpdate)
                                .build();
                            schedulableNextDate.put(lastUpdate.uid(), FlowWithWorkerTriggerNextDate.of(flowWithWorkerTrigger));

                        } catch (Exception e) {
                            logError(conditionContext, flowAndTrigger.flow(), flowAndTrigger.trigger(), e);
                        }
                    }
                });
        }

        this.isReady = true;
    }

    private void clusterEventQueue(Either<ClusterEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a cluster event: {}", either.getRight().getMessage());
            return;
        }

        ClusterEvent clusterEvent = either.getLeft();
        log.info("Cluster event received: {}", clusterEvent);
        switch (clusterEvent.eventType()) {
            case MAINTENANCE_ENTER -> enterMaintenance();
            case MAINTENANCE_EXIT -> exitMaintenance();
        }
    }

    private void enterMaintenance() {
        this.executionQueue.pause();
        this.triggerQueue.pause();
        this.workerJobQueue.pause();
        this.workerTriggerResultQueue.pause();
        this.executionKilledQueue.pause();
        this.pauseAdditionalQueues();

        this.isPaused.set(true);
        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.executionQueue.resume();
        this.triggerQueue.resume();
        this.workerJobQueue.resume();
        this.workerTriggerResultQueue.resume();
        this.executionKilledQueue.resume();
        this.resumeAdditionalQueues();

        this.isPaused.set(false);
        this.setState(ServiceState.RUNNING);
    }

    protected void resumeAdditionalQueues() {
        // by default: do nothing
    }

    protected void pauseAdditionalQueues() {
        // by default: do nothing
    }

    private ZonedDateTime nextEvaluationDate(AbstractTrigger abstractTrigger) throws InvalidTriggerConfigurationException {
        if (abstractTrigger instanceof PollingTriggerInterface interval) {
            return interval.nextEvaluationDate();
        } else {
            return ZonedDateTime.now();
        }
    }

    private ZonedDateTime nextEvaluationDate(AbstractTrigger abstractTrigger, ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception, InvalidTriggerConfigurationException {
        if (abstractTrigger instanceof PollingTriggerInterface interval) {
            return interval.nextEvaluationDate(conditionContext, last);
        } else {
            return ZonedDateTime.now();
        }
    }

    private Duration interval(AbstractTrigger abstractTrigger) {
        if (abstractTrigger instanceof PollingTriggerInterface interval) {
            return interval.getInterval();
        } else {
            return Duration.ofSeconds(1);
        }
    }

    private List<FlowWithTriggers> computeSchedulable(List<FlowWithSource> flows, List<Trigger> triggerContextsToEvaluate, ScheduleContextInterface scheduleContext) {
        List<String> flowToKeep = triggerContextsToEvaluate.stream().map(Trigger::getFlowId).toList();
        List<String> flowIds = flows.stream().map(FlowId::uidWithoutRevision).toList();
        Map<String, Trigger> triggerById = triggerContextsToEvaluate.stream().collect(Collectors.toMap(HasUID::uid, Function.identity()));

        // delete trigger which flow has been deleted
        triggerContextsToEvaluate.stream()
            .filter(trigger -> !flowIds.contains(FlowId.uid(trigger)))
            .forEach(trigger -> {
                try {
                    this.triggerState.delete(trigger);
                } catch (QueueException e) {
                    log.error("Unable to delete the trigger: {}.{}.{}", trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId(), e);
                }
            });

        return flows
            .stream()
            .filter(flow -> flowToKeep.contains(flow.getId()))
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .filter(flow -> !flow.isDisabled() && !(flow instanceof FlowWithException))
            .map(flow -> pluginDefaultService.injectAllDefaults(flow, log))
            .filter(Objects::nonNull) // can occur if injecting default fail
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled() && abstractTrigger instanceof WorkerTriggerInterface)
                .map(abstractTrigger -> {
                    RunContext runContext = runContextFactory.of(flow, abstractTrigger);
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                    Trigger triggerContext;
                    Trigger lastTrigger = triggerById.get(Trigger.uid(flow, abstractTrigger));
                    // If a trigger is not found in triggers to evaluate, then we ignore it
                    if (lastTrigger == null) {
                        return null;
                        // Backwards compatibility: we add a next execution date that we compute, this avoids re-triggering all existing triggers
                    } else if (lastTrigger.getNextExecutionDate() == null) {
                        try {
                            triggerContext = lastTrigger.toBuilder()
                                .nextExecutionDate(this.nextEvaluationDate(abstractTrigger, conditionContext, Optional.of(lastTrigger)))
                                .build();
                        } catch (InvalidTriggerConfigurationException e) {
                            logError(conditionContext, flow, abstractTrigger, e);
                            disableInvalidTrigger(flow, abstractTrigger, e);
                            return null;
                        } catch (Exception e) {
                            logError(conditionContext, flow, abstractTrigger, e);
                            return null;
                        }
                        this.triggerState.save(triggerContext, scheduleContext, "/kestra/services/scheduler/compute-schedulable/save/lastTrigger-nextDate-null");
                    } else {
                        triggerContext = lastTrigger;
                    }
                    return new FlowWithTriggers(
                        flow,
                        abstractTrigger,
                        triggerContext,
                        conditionContext.withVariables(
                            ImmutableMap.of("trigger",
                                ImmutableMap.of("date", triggerContext.getNextExecutionDate() != null ?
                                    triggerContext.getNextExecutionDate() : now())
                            ))
                    );
                })
            )
            .filter(Objects::nonNull).toList();
    }

    private void disableInvalidTrigger(TriggerContext triggerContext, Throwable e) {
        try {
            var disabledTrigger = Trigger.builder()
                .tenantId(triggerContext.getTenantId())
                .namespace(triggerContext.getNamespace())
                .flowId(triggerContext.getFlowId())
                .triggerId(triggerContext.getTriggerId())
                .date(triggerContext.getDate())
                .backfill(triggerContext.getBackfill())
                .stopAfter(triggerContext.getStopAfter())
                .disabled(true)
                .updatedDate(Instant.now())
                .build();

            triggerState.update(disabledTrigger);

            triggerQueue.emit(disabledTrigger);

            log.warn("Disabled trigger {}.{} due to invalid configuration: {}", disabledTrigger.getFlowId(), disabledTrigger.getTriggerId(), e.getMessage());
        } catch (Exception ex) {
            log.error("Failed to disable trigger {}.{}: {}", triggerContext.getFlowId(), triggerContext.getTriggerId(), ex.getMessage(), ex);
        }
    }

    private void disableInvalidTrigger(FlowWithSource flow, AbstractTrigger trigger, Throwable e) {
        var disabledTrigger = Trigger.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .disabled(true)
            .updatedDate(Instant.now())
            .build();

        disableInvalidTrigger(disabledTrigger, e);
    }

    private void disableInvalidTrigger(FlowWithWorkerTrigger f, Throwable e) {
        disableInvalidTrigger(f.getTriggerContext(), e);
    }

    abstract public void handleNext(List<FlowWithSource> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer);

    public List<FlowWithTriggers> schedulerTriggers() {
        Map<String, FlowWithSource> flows = getFlowsWithDefaults().stream()
            .collect(Collectors.toMap(FlowInterface::uidWithoutRevision, Function.identity()));

        return this.triggerState.findAllForAllTenants().stream()
            .filter(trigger -> flows.containsKey(trigger.flowUid()))
            .map(trigger ->
                new FlowWithTriggers(
                    flows.get(trigger.flowUid()),
                    ListUtils.emptyOnNull(flows.get(trigger.flowUid()).getTriggers()).stream().filter(t -> t.getId().equals(trigger.getTriggerId())).findFirst().orElse(null),
                    trigger,
                    null
                )
            ).toList();
    }

    private void handle() {
        if (!isReady()) {
            log.warn("Scheduler is not ready, waiting");
            return;
        }

        if (this.isPaused.get()) {
            return;
        }

        ZonedDateTime now = now();

        final List<FlowWithSource> flows = this.flowListeners.flows();

        this.handleNext(flows, now, (triggers, scheduleContext) -> {
            if (triggers.isEmpty()) {
                return;
            }

            List<Trigger> triggerContextsToEvaluate = triggers.stream()
                .filter(trigger -> Boolean.FALSE.equals(trigger.getDisabled()))
                .toList();

            List<FlowWithTriggers> schedulable = this.computeSchedulable(flows, triggerContextsToEvaluate, scheduleContext);

            metricRegistry
                .counter(MetricRegistry.METRIC_SCHEDULER_LOOP_COUNT, MetricRegistry.METRIC_SCHEDULER_LOOP_COUNT_DESCRIPTION)
                .increment();

            if (log.isTraceEnabled()) {
                log.trace(
                    "Scheduler next iteration for {} with {} schedulables of {} flows",
                    now,
                    schedulable.size(),
                    this.flowListeners.flows().size()
                );
            }

            // Get all triggers that are ready for evaluation
            List<FlowWithWorkerTriggerNextDate> readyForEvaluate = schedulable
                .stream()
                .map(flowWithTriggers -> FlowWithWorkerTrigger.builder()
                    .flow(flowWithTriggers.getFlow())
                    .abstractTrigger(flowWithTriggers.getAbstractTrigger())
                    .conditionContext(flowWithTriggers.getConditionContext())
                    .triggerContext(flowWithTriggers.triggerContext
                        .toBuilder()
                        .date(now())
                        .stopAfter(flowWithTriggers.getAbstractTrigger().getStopAfter())
                        .build()
                    )
                    .build()
                )
                .filter(f -> f.getTriggerContext().getEvaluateRunningDate() == null)
                .map(FlowWithWorkerTriggerNextDate::of)
                .filter(Objects::nonNull)
                .toList();

            if (log.isTraceEnabled()) {
                log.trace(
                    "Scheduler will evaluate for {} with {} readyForEvaluate of {} schedulables",
                    now,
                    readyForEvaluate.size(),
                    schedulable.size()
                );
            }

            metricRegistry
                .counter(MetricRegistry.METRIC_SCHEDULER_EVALUATE_COUNT, MetricRegistry.METRIC_SCHEDULER_EVALUATE_COUNT_DESCRIPTION)
                .increment(readyForEvaluate.size());

            // submit ready one to the worker
            readyForEvaluate
                .forEach(f -> {
                    schedulableNextDate.put(f.getTriggerContext().uid(), f);
                    Logger logger = f.getConditionContext().getRunContext().logger();
                    try {
                        // conditionService.areValid can fail, so we cannot execute it early as we need to try/catch and send a failed executions
                        List<Condition> conditions = f.getAbstractTrigger().getConditions() != null ? f.getAbstractTrigger().getConditions() : Collections.emptyList();
                        boolean shouldEvaluate = conditionService.areValid(conditions, f.getConditionContext());
                        if (shouldEvaluate) {
                            if (this.interval(f.getAbstractTrigger()) != null) {
                                // If it has an interval, the Worker will execute the trigger.
                                // Normally, only the Schedule trigger has no interval.
                                Trigger triggerRunning = Trigger.of(f.getTriggerContext(), now);
                                var flowWithTrigger = f.toBuilder().triggerContext(triggerRunning).build();
                                try {
                                    this.triggerState.save(triggerRunning, scheduleContext, "/kestra/services/scheduler/handle/save/on-eval-true/polling");
                                    this.sendWorkerTriggerToWorker(flowWithTrigger);
                                } catch (InternalException e) {
                                    Logs.logTrigger(
                                        f.getTriggerContext(),
                                        logger,
                                        Level.ERROR,
                                        "Unable to send worker trigger to worker",
                                        e
                                    );
                                }
                            } else if (f.getAbstractTrigger() instanceof Schedulable schedule) {
                                // This is the Schedule, all other triggers should have an interval.
                                // So we evaluate it now as there is no need to send it to the worker.
                                // Schedule didn't use the triggerState to allow backfill.
                                Optional<SchedulerExecutionWithTrigger> schedulerExecutionWithTrigger = evaluateScheduleTrigger(f);
                                if (schedulerExecutionWithTrigger.isPresent()) {
                                    this.handleEvaluateSchedulingTriggerResult(schedule, schedulerExecutionWithTrigger.get(), f.getConditionContext(), scheduleContext);
                                }
                                else{
                                    // compute next date and save the trigger to avoid evaluating it each second
                                    Trigger trigger = Trigger.fromEvaluateFailed(
                                        f.getTriggerContext(),
                                        schedule.nextEvaluationDate(f.getConditionContext(), Optional.of(f.getTriggerContext()))
                                    );
                                    trigger = trigger.checkBackfill();
                                    this.triggerState.save(trigger, scheduleContext, "/kestra/services/scheduler/handle/save/on-eval-true/schedule");
                                }
                            } else {
                                Logs.logTrigger(
                                    f.getTriggerContext(),
                                    logger,
                                    Level.ERROR,
                                    "Worker trigger must have an interval (except the Schedule and Streaming)"
                                );
                            }
                        } else {
                            ZonedDateTime nextExecutionDate = null;
                            try {
                                nextExecutionDate = this.nextEvaluationDate(f.getAbstractTrigger(), f.getConditionContext(), Optional.of(f.getTriggerContext()));
                            } catch (InvalidTriggerConfigurationException e) {
                                logError(f, e);
                                disableInvalidTrigger(f, e);
                                return;
                            } catch (Exception e) {
                                logError(f, e);
                            }
                            var trigger = f.getTriggerContext().toBuilder().nextExecutionDate(nextExecutionDate).build().checkBackfill();
                            this.triggerState.save(trigger, scheduleContext, "/kestra/services/scheduler/handle/save/on-eval-false");
                        }
                    } catch (Exception ie) {
                        // validate schedule condition can fail to render variables
                        // in this case, we send a failed execution so the trigger is not evaluated each second.
                        logger.error("Unable to evaluate the trigger '{}'", f.getAbstractTrigger().getId(), ie);
                       handleFailedEvaluatedTrigger(f, scheduleContext, ie);
                    }
                });
        });
        metricRegistry
            .timer(MetricRegistry.METRIC_SCHEDULER_EVALUATION_LOOP_DURATION, MetricRegistry.METRIC_SCHEDULER_EVALUATION_LOOP_DURATION_DESCRIPTION)
            .record(Duration.between(now, ZonedDateTime.now()));
    }

    private List<FlowWithSource> getFlowsWithDefaults() {
        return this.flowListeners.flows().stream()
            .map(flow -> pluginDefaultService.injectAllDefaults(flow, log))
            .filter(Objects::nonNull)
            .toList();
    }

    private void handleEvaluateWorkerTriggerResult(SchedulerExecutionWithTrigger result, ZonedDateTime
        nextExecutionDate, AbstractTrigger abstractTrigger) {
        Optional.ofNullable(result)
            .ifPresent(executionWithTrigger -> {
                    log(executionWithTrigger);

                    Trigger trigger = Trigger.of(
                        executionWithTrigger.getTriggerContext(),
                        executionWithTrigger.getExecution(),
                        nextExecutionDate
                    );

                    // if the trigger is allowed to run concurrently we do not attached the executio-id to the trigger state
                    // i.e., the trigger will not be locked
                    if (abstractTrigger.isAllowConcurrent()) {
                        trigger = trigger.toBuilder().executionId(null).build();
                    }
                
                    // Worker triggers result is evaluated in another thread with the workerTriggerResultQueue.
                    // We can then update the trigger directly.
                    this.saveLastTriggerAndEmitExecution(executionWithTrigger.getExecution(), trigger, triggerToSave -> this.triggerState.update(triggerToSave));
                }
            );
    }

    private void handleEvaluateSchedulingTriggerResult(Schedulable schedule, SchedulerExecutionWithTrigger
        result, ConditionContext conditionContext, ScheduleContextInterface scheduleContext) throws Exception {
        log(result);
        Trigger trigger = Trigger.of(
            result.getTriggerContext(),
            result.getExecution(),
            schedule.nextEvaluationDate(conditionContext, Optional.of(result.getTriggerContext()))
        );
        trigger = trigger.checkBackfill();

        // if the execution is already failed due to failed execution, we reset the trigger now
        if (result.getExecution().getState().getCurrent() == State.Type.FAILED) {
            trigger = trigger.resetExecution(State.Type.FAILED);
        }
        
        // if the trigger is allowed to run concurrently we do not attached the executio-id to the trigger state
        // i.e., the trigger will not be locked
        if (((AbstractTrigger)schedule).isAllowConcurrent()) {
            trigger = trigger.toBuilder().executionId(null).build();
        }

        // Schedule triggers are being executed directly from the handle method within the context where triggers are locked.
        // So we must save them by passing the scheduleContext.
        this.saveLastTriggerAndEmitExecution(result.getExecution(), trigger, triggerToSave -> this.triggerState.save(triggerToSave, scheduleContext, "/kestra/services/scheduler/handleEvaluateSchedulingTriggerResult/save"));
    }

    protected void saveLastTriggerAndEmitExecution(Execution execution, Trigger
        trigger, Consumer<Trigger> saveAction) {
        saveAction.accept(trigger);
        this.emitExecution(execution, trigger);
    }

    private void emitExecution(Execution execution, TriggerContext trigger) {
        // we need to be sure that the tenantId is propagated from the trigger to the execution
        var newExecution = execution.withTenantId(trigger.getTenantId());
        try {
            this.executionQueue.emit(newExecution);
            this.executionEventPublisher.publishEvent(new CrudEvent<>(newExecution, CrudEventType.CREATE));
        } catch (QueueException e) {
            try {
                Execution failedExecution = fail(newExecution, e);
                this.executionQueue.emit(failedExecution);
                this.executionEventPublisher.publishEvent(new CrudEvent<>(failedExecution, CrudEventType.CREATE));
            } catch (QueueException ex) {
                log.error("Unable to emit the execution", ex);
            }
        }
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        try {
            logQueue.emitAsync(failedExecution.getLogs());
        } catch (QueueException ex) {
            // fail silently
        }
        return failedExecution.getExecution().getState().isFailed() ? failedExecution.getExecution() :  failedExecution.getExecution().withState(State.Type.FAILED);
    }

    private void executionMonitor() {
        try {
            // Retrieve triggers with non-null execution_id from all corresponding virtual nodes
            ZonedDateTime now = ZonedDateTime.now();
            List<Trigger> triggers = this.triggerState.findByNextExecutionDateReadyButLockedTriggers(now);
            if (CollectionUtils.isEmpty(triggers)) {
                log.debug("executionMonitor triggers is empty, skip");
                return;
            }
            triggers.forEach(lastTrigger -> {
                Optional<Execution> execution = executionState.findById(lastTrigger.getTenantId(), lastTrigger.getExecutionId());
                // executionState hasn't received the execution, we skip
                if (execution.isEmpty()) {
                    if (lastTrigger.getUpdatedDate() != null) {
                        metricRegistry
                            .timer(MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION, MetricRegistry.METRIC_SCHEDULER_EXECUTION_MISSING_DURATION_DESCRIPTION, metricRegistry.tags(lastTrigger))
                            .record(Duration.between(lastTrigger.getUpdatedDate(), Instant.now()));
                    }
                    if (lastTrigger.getUpdatedDate() == null || lastTrigger.getUpdatedDate().plusSeconds(60).isBefore(Instant.now())) {
                        Logs.logTrigger(
                            lastTrigger,
                            Level.WARN,
                            "Execution '{}' is not found, schedule is blocked since '{}'",
                            lastTrigger.getExecutionId(),
                            lastTrigger.getUpdatedDate()
                        );
                    }
                    return;
                }
                if (lastTrigger.getUpdatedDate() != null) {
                    metricRegistry
                        .timer(MetricRegistry.METRIC_SCHEDULER_EXECUTION_LOCK_DURATION, MetricRegistry.METRIC_SCHEDULER_EXECUTION_LOCK_DURATION_DESCRIPTION, metricRegistry.tags(lastTrigger))
                        .record(Duration.between(lastTrigger.getUpdatedDate(), Instant.now()));
                }
                if (log.isDebugEnabled()) {
                    Logs.logTrigger(
                        lastTrigger,
                        Level.DEBUG,
                        "Execution '{}' is still '{}', updated at '{}'",
                        lastTrigger.getExecutionId(),
                        execution.get().getState().getCurrent(),
                        lastTrigger.getUpdatedDate()
                    );
                }
            });
        } catch (Exception e) {
            log.error("executionMonitor error", e);
        }
    }

    private void log(SchedulerExecutionWithTrigger executionWithTrigger) {
        metricRegistry
            .counter(MetricRegistry.METRIC_SCHEDULER_TRIGGER_COUNT, MetricRegistry.METRIC_SCHEDULER_TRIGGER_COUNT_DESCRIPTION, metricRegistry.tags(executionWithTrigger.getExecution()))
            .increment();

        ZonedDateTime now = now();

        if (executionWithTrigger.getExecution().getTrigger() != null &&
            executionWithTrigger.getExecution().getTrigger().getVariables() != null &&
            executionWithTrigger.getExecution().getTrigger().getVariables().containsKey("next")
        ) {
            Object nextVariable = executionWithTrigger.getExecution().getTrigger().getVariables().get("next");

            ZonedDateTime next = (nextVariable != null) ? ZonedDateTime.parse((CharSequence) nextVariable) : null;

            // Exclude backfills
            // FIXME : "late" are not excluded and can increase delay value (false positive)
            if (next != null && now.isBefore(next)) {
                metricRegistry
                    .timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_DELAY_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_DELAY_DURATION_DESCRIPTION, metricRegistry.tags(executionWithTrigger.getExecution()))
                    .record(Duration.between(
                        executionWithTrigger.getTriggerContext().getDate(), now
                    ));
            }
        }

        Logs.logTrigger(
            executionWithTrigger.getTriggerContext(),
            Level.INFO,
            "Scheduled execution {} at '{}' started at '{}'",
            executionWithTrigger.getExecution().getId(),
            executionWithTrigger.getTriggerContext().getDate(),
            now
        );
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private Optional<SchedulerExecutionWithTrigger> evaluateScheduleTrigger(FlowWithWorkerTrigger flowWithTrigger) {
        return metricRegistry.timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION_DESCRIPTION, metricRegistry.tags(flowWithTrigger.getAbstractTrigger()))
            .record(() -> {
                try {

                    // mutability dirty hack that forces the creation of a new triggerExecutionId
                    DefaultRunContext runContext = (DefaultRunContext) flowWithTrigger.getConditionContext().getRunContext();
                    runContextInitializer.forScheduler(
                        runContext,
                        flowWithTrigger.getTriggerContext(),
                        flowWithTrigger.getAbstractTrigger()
                    );

                    Optional<Execution> evaluate = ((Schedulable) flowWithTrigger.getAbstractTrigger()).evaluate(
                        flowWithTrigger.getConditionContext(),
                        flowWithTrigger.getTriggerContext()
                    );

                    if (log.isDebugEnabled()) {
                        Logs.logTrigger(
                            flowWithTrigger.getTriggerContext(),
                            Level.DEBUG,
                            "[type: {}] {}",
                            flowWithTrigger.getAbstractTrigger().getType(),
                            evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
                        );
                    }

                    flowWithTrigger.getConditionContext().getRunContext().cleanup();

                    return evaluate.map(execution -> new SchedulerExecutionWithTrigger(
                        execution,
                        flowWithTrigger.getTriggerContext()
                    ));
                } catch (Exception e) {
                    logError(flowWithTrigger, e);
                    Execution failedExecution =  createFailedExecution( flowWithTrigger, e);
                    this.emitExecution(failedExecution, flowWithTrigger.getTriggerContext());
                    return Optional.empty();
                }
            });
    }
    private Execution createFailedExecution(FlowWithWorkerTrigger flowWithTrigger, Throwable e){
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flowWithTrigger.getTriggerContext().getTenantId())
            .namespace(flowWithTrigger.getTriggerContext().getNamespace())
            .flowId(flowWithTrigger.getTriggerContext().getFlowId())
            .flowRevision(flowWithTrigger.getFlow().getRevision())
            .labels(LabelService.labelsExcludingSystem(flowWithTrigger.getFlow()))
            .state(new State().withState(State.Type.FAILED))
            .build();
        Logger logger = runContextFactory.of(flowWithTrigger.getFlow(), execution).logger();
        logger.error("[trigger: {}] [date: {}] Evaluate Failed with error '{}'" , flowWithTrigger.getAbstractTrigger().getId(), now(), e.getMessage());
        return execution;
    }
   private void handleFailedEvaluatedTrigger(FlowWithWorkerTrigger flowWithTrigger, ScheduleContextInterface scheduleContext, Throwable e ){

        Execution execution = createFailedExecution(flowWithTrigger, e);
        ZonedDateTime nextExecutionDate;
        try {
            nextExecutionDate = this.nextEvaluationDate(flowWithTrigger.getAbstractTrigger());
        } catch (InvalidTriggerConfigurationException e2) {
            logError(flowWithTrigger, e2);
            disableInvalidTrigger(flowWithTrigger, e2);
            return;
        }

        var trigger = flowWithTrigger.getTriggerContext().resetExecution(State.Type.FAILED, nextExecutionDate);
        trigger = trigger.checkBackfill();
        this.saveLastTriggerAndEmitExecution(execution, trigger, triggerToSave -> this.triggerState.save(triggerToSave, scheduleContext, "/kestra/services/scheduler/handle/save/on-error"));

    }
    private void logError(FlowWithWorkerTrigger flowWithWorkerTriggerNextDate, Throwable e) {
        Logger logger = flowWithWorkerTriggerNextDate.getConditionContext().getRunContext().logger();

        Logs.logTrigger(
            flowWithWorkerTriggerNextDate.getTriggerContext(),
            logger,
            Level.WARN,
            "[date: {}] Evaluate Failed with error '{}'",
            flowWithWorkerTriggerNextDate.getTriggerContext().getDate(),
            e.getMessage(),
            e
        );

        if (logger.isTraceEnabled()) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }

    private void logError(ConditionContext conditionContext, FlowWithSource flow, AbstractTrigger
        trigger, Throwable e) {
        Logger logger = conditionContext.getRunContext().logger();

        Logs.logExecution(
            flow,
            logger,
            Level.ERROR,
            "[trigger: {}] [date: {}] Evaluate Failed with error '{}'",
            trigger.getId(),
            now(),
            e.getMessage(),
            e
        );
    }

    private void sendWorkerTriggerToWorker(FlowWithWorkerTrigger flowWithTrigger) throws InternalException {
        if (log.isDebugEnabled()) {
            Logs.logTrigger(
                flowWithTrigger.getTriggerContext(),
                Level.DEBUG,
                "[date: {}] Scheduling evaluation to the worker",
                flowWithTrigger.getTriggerContext().getDate()
            );
        }

        var workerTrigger = WorkerTrigger
            .builder()
            .trigger(flowWithTrigger.abstractTrigger)
            .triggerContext(flowWithTrigger.triggerContext)
            .conditionContext(flowWithTrigger.conditionContext)
            .build();
        try {
            Optional<WorkerGroup> workerGroup = workerGroupService.resolveGroupFromJob(flowWithTrigger.getFlow(), workerTrigger);
            if (workerGroup.isPresent()) {
                // Check if the worker group exist
                String tenantId = flowWithTrigger.getFlow().getTenantId();
                RunContext runContext = flowWithTrigger.conditionContext.getRunContext();
                String workerGroupKey = runContext.render(workerGroup.get().getKey());
                if (workerGroupExecutorInterface.isWorkerGroupExistForKey(workerGroupKey, tenantId)) {
                    // Check whether at-least one worker is available
                    if (workerGroupExecutorInterface.isWorkerGroupAvailableForKey(workerGroupKey)) {
                        this.workerJobQueue.emit(workerGroupKey, workerTrigger);
                    } else {
                        WorkerGroup.Fallback fallback = workerGroup.map(WorkerGroup::getFallback).orElse(WorkerGroup.Fallback.WAIT);
                        switch(fallback) {
                            case FAIL -> runContext.logger()
                                    .error("No workers are available for worker group '{}', ignoring the trigger.", workerGroupKey);
                            case CANCEL -> runContext.logger()
                                    .warn("No workers are available for worker group '{}', ignoring the trigger.", workerGroupKey);
                            case WAIT -> {
                                runContext.logger()
                                    .info("No workers are available for worker group '{}', waiting for one to be available.", workerGroupKey);
                                this.workerJobQueue.emit(workerGroupKey, workerTrigger);
                            }
                        };
                    }
                } else {
                    runContext.logger().error("No worker group exist for key '{}', ignoring the trigger.", workerGroupKey);
                }
            } else {
                this.workerJobQueue.emit(workerTrigger);
            }
        } catch (QueueException e) {
            log.error("Unable to emit the Worker Trigger job", e);
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    @PreDestroy
    public void close() {
        close(null);
    }

    protected void close(final @Nullable Runnable onClose) {
        if (shutdown.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("Terminating");
            }

            setState(ServiceState.TERMINATING);
            this.receiveCancellations.forEach(Runnable::run);
            ExecutorsUtils.closeScheduledThreadPool(this.scheduleExecutor, Duration.ofSeconds(5), List.of(scheduledFuture));
            ExecutorsUtils.closeScheduledThreadPool(executionMonitorExecutor, Duration.ofSeconds(5), List.of(executionMonitorFuture));
            try {
                if (onClose != null) {
                    onClose.run();
                }
            } catch (Exception e) {
                log.error("Unexpected error while terminating scheduler.", e);
            }
            setState(ServiceState.TERMINATED_GRACEFULLY);

            if (log.isDebugEnabled()) {
                log.debug("Closed ({}).", state.get().name());
            }
        }
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    private static class FlowWithWorkerTrigger {
        private FlowWithSource flow;
        private AbstractTrigger abstractTrigger;
        private Trigger triggerContext;
        private ConditionContext conditionContext;

        public FlowWithWorkerTrigger from(FlowWithSource flow) throws InternalException {
            AbstractTrigger abstractTrigger = flow.getTriggers()
                .stream()
                .filter(a -> a.getId().equals(this.abstractTrigger.getId()) && a instanceof WorkerTriggerInterface)
                .findFirst()
                .orElseThrow(() -> new InternalException("Couldn't find the trigger '" + this.abstractTrigger.getId() + "' on flow '" + flow.uid() + "'"));

            return this.toBuilder()
                .flow(flow)
                .abstractTrigger(abstractTrigger)
                .build();
        }
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class FlowWithWorkerTriggerNextDate extends FlowWithWorkerTrigger {
        private ZonedDateTime next;

        private static FlowWithWorkerTriggerNextDate of(FlowWithWorkerTrigger f) {
            return FlowWithWorkerTriggerNextDate.builder()
                .flow(f.getFlow())
                .abstractTrigger(f.getAbstractTrigger())
                .conditionContext(f.getConditionContext())
                .triggerContext(Trigger.builder()
                    .tenantId(f.getTriggerContext().getTenantId())
                    .namespace(f.getTriggerContext().getNamespace())
                    .flowId(f.getTriggerContext().getFlowId())
                    .triggerId(f.getTriggerContext().getTriggerId())
                    .date(f.getTriggerContext().getNextExecutionDate())
                    .nextExecutionDate(f.getTriggerContext().getNextExecutionDate())
                    .backfill(f.getTriggerContext().getBackfill())
                    .stopAfter(f.getTriggerContext().getStopAfter())
                    .build()
                )
                .next(f.getTriggerContext().getNextExecutionDate())
                .build();
        }
    }

    @AllArgsConstructor
    @Getter
    @Builder(toBuilder = true)
    public static class FlowWithTriggers {
        private final FlowWithSource flow;
        private final AbstractTrigger abstractTrigger;
        private final Trigger triggerContext;
        private final ConditionContext conditionContext;

        public String uid() {
            return Trigger.uid(flow, abstractTrigger);
        }
    }

    protected void setState(final ServiceState state) {
        this.state.set(state);
        serviceStateEventPublisher.publishEvent(new ServiceStateChangeEvent(this));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceType getType() {
        return ServiceType.SCHEDULER;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceState getState() {
        return state.get();
    }
}
