package io.kestra.core.schedulers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.*;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.*;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
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
public abstract class AbstractScheduler implements Scheduler, Service {
    protected final ApplicationContext applicationContext;
    protected final QueueInterface<Execution> executionQueue;
    protected final QueueInterface<Trigger> triggerQueue;
    private final QueueInterface<WorkerJob> workerTaskQueue;
    private final WorkerTriggerResultQueueInterface workerTriggerResultQueue;
    private final QueueInterface<ExecutionKilled> executionKilledQueue;
    @SuppressWarnings("rawtypes") private final Optional<QueueInterface> clusterEventQueue;
    protected final FlowListenersInterface flowListeners;
    private final RunContextFactory runContextFactory;
    private final RunContextInitializer runContextInitializer;
    private final MetricRegistry metricRegistry;
    private final ConditionService conditionService;
    private final PluginDefaultService pluginDefaultService;
    private final WorkerGroupService workerGroupService;
    private final LogService logService;
    protected SchedulerExecutionStateInterface executionState;

    // must be volatile as it's updated by the flow listener thread and read by the scheduleExecutor thread
    private volatile Boolean isReady = false;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();

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
        this.workerTaskQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED));
        this.executionKilledQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED));
        this.workerTriggerResultQueue = applicationContext.getBean(WorkerTriggerResultQueueInterface.class);
        this.clusterEventQueue = applicationContext.findBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.CLUSTER_EVENT_NAMED));
        this.flowListeners = flowListeners;
        this.runContextFactory = applicationContext.getBean(RunContextFactory.class);
        this.runContextInitializer = applicationContext.getBean(RunContextInitializer.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.pluginDefaultService = applicationContext.getBean(PluginDefaultService.class);
        this.workerGroupService = applicationContext.getBean(WorkerGroupService.class);
        this.logService = applicationContext.getBean(LogService.class);
        this.serviceStateEventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.executionEventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
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

        ScheduledFuture<?> handle = scheduleExecutor.scheduleAtFixedRate(
            this::handle,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exception on the main thread
        Thread.ofVirtual().name("scheduler-listener").start(
            () -> {
                Await.until(handle::isDone);

                try {
                    handle.get();
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
                } else if (workerTriggerResult.getSuccess() && workerTriggerResult.getExecution().isPresent()) {
                    SchedulerExecutionWithTrigger triggerExecution = new SchedulerExecutionWithTrigger(
                        workerTriggerResult.getExecution().get(),
                        workerTriggerResult.getTriggerContext()
                    );
                    ZonedDateTime nextExecutionDate = this.nextEvaluationDate(workerTriggerResult.getTrigger());
                    this.handleEvaluateWorkerTriggerResult(triggerExecution, nextExecutionDate);
                } else {
                    ZonedDateTime nextExecutionDate = this.nextEvaluationDate(workerTriggerResult.getTrigger());
                    this.triggerState.update(Trigger.of(workerTriggerResult.getTriggerContext(), nextExecutionDate));
                }
            }
        ));

        // listen to cluster events
        this.clusterEventQueue.ifPresent(clusterEventQueueInterface -> this.receiveCancellations.addFirst(((QueueInterface<ClusterEvent>) clusterEventQueueInterface).receive(this::clusterEventQueue)));

        setState(ServiceState.RUNNING);
    }

    // Initialized local trigger state,
    // and if some flows were created outside the box, for example from the CLI,
    // then we may have some triggers that are not created yet.
    private void initializedTriggers(List<FlowWithSource> flows) {
        record FlowAndTrigger(FlowWithSource flow, AbstractTrigger trigger) {
        }
        List<Trigger> triggers = triggerState.findAllForAllTenants();

        flows
            .stream()
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .flatMap(flow -> flow.getTriggers().stream().filter(trigger -> trigger instanceof WorkerTriggerInterface).map(trigger -> new FlowAndTrigger(flow, trigger)))
            .forEach(flowAndTrigger -> {
                Optional<Trigger> trigger = triggers.stream().filter(t -> t.uid().equals(Trigger.uid(flowAndTrigger.flow(), flowAndTrigger.trigger()))).findFirst(); // must have one or none
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
                            .workerTrigger((WorkerTriggerInterface) flowAndTrigger.trigger())
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
                        } else if (recoverMissedSchedules == RecoverMissedSchedules.NONE) {
                            lastUpdate = trigger.get().toBuilder().nextExecutionDate(schedule.nextEvaluationDate()).build();

                            this.triggerState.update(lastUpdate);
                        }
                        // Used for schedulableNextDate
                        FlowWithWorkerTrigger flowWithWorkerTrigger = FlowWithWorkerTrigger.builder()
                            .flow(flowAndTrigger.flow())
                            .abstractTrigger(flowAndTrigger.trigger())
                            .workerTrigger((WorkerTriggerInterface) flowAndTrigger.trigger())
                            .conditionContext(conditionContext)
                            .triggerContext(lastUpdate)
                            .build();
                        schedulableNextDate.put(lastUpdate.uid(), FlowWithWorkerTriggerNextDate.of(flowWithWorkerTrigger));

                    } catch (Exception e) {
                        logError(conditionContext, flowAndTrigger.flow(), flowAndTrigger.trigger(), e);
                    }
                }
            });

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
            case MAINTENANCE_ENTER -> {
                this.executionQueue.pause();
                this.triggerQueue.pause();
                this.workerTaskQueue.pause();
                this.workerTriggerResultQueue.pause();
                this.executionKilledQueue.pause();
                this.pauseAdditionalQueues();

                this.isPaused.set(true);
                this.setState(ServiceState.MAINTENANCE);
            }
            case MAINTENANCE_EXIT -> {
                this.executionQueue.resume();
                this.triggerQueue.resume();
                this.workerTaskQueue.resume();
                this.workerTriggerResultQueue.resume();
                this.executionKilledQueue.resume();
                this.resumeAdditionalQueues();

                this.isPaused.set(false);
                this.setState(ServiceState.RUNNING);
            }
        }
    }

    protected void resumeAdditionalQueues() {
        // by default: do nothing
    }

    protected void pauseAdditionalQueues() {
        // by default: do nothing
    }

    private ZonedDateTime nextEvaluationDate(AbstractTrigger abstractTrigger) {
        if (abstractTrigger instanceof PollingTriggerInterface interval) {
            return interval.nextEvaluationDate();
        } else {
            return ZonedDateTime.now();
        }
    }

    private ZonedDateTime nextEvaluationDate(AbstractTrigger abstractTrigger, ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
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

        return flows
            .stream()
            .filter(flow -> flowToKeep.contains(flow.getId()))
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .filter(flow -> !flow.isDisabled() && !(flow instanceof FlowWithException))
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled() && abstractTrigger instanceof WorkerTriggerInterface)
                .map(abstractTrigger -> {
                    RunContext runContext = runContextFactory.of(flow, abstractTrigger);
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                    Trigger triggerContext = null;
                    Trigger lastTrigger = triggerContextsToEvaluate
                        .stream()
                        .filter(triggerContextToFind -> triggerContextToFind.uid().equals(Trigger.uid(flow, abstractTrigger)))
                        .findFirst()
                        .orElse(null);
                    // If a trigger is not found in triggers to evaluate, then we ignore it
                    if (lastTrigger == null) {
                        return null;
                        // Backwards compatibility: we add a next execution date that we compute, this avoids re-triggering all existing triggers
                    } else if (lastTrigger.getNextExecutionDate() == null) {
                        try {
                            triggerContext = lastTrigger.toBuilder()
                                .nextExecutionDate(this.nextEvaluationDate(abstractTrigger, conditionContext, Optional.of(lastTrigger)))
                                .build();
                        } catch (Exception e) {
                            logError(conditionContext, flow, abstractTrigger, e);
                            return null;
                        }
                        this.triggerState.save(triggerContext, scheduleContext);
                    } else {
                        triggerContext = lastTrigger;
                    }
                    return new FlowWithTriggers(
                        flow,
                        abstractTrigger,
                        triggerContext,
                        runContext,
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

    abstract public void handleNext(List<FlowWithSource> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer);

    public List<FlowWithTriggers> schedulerTriggers() {
        Map<String, FlowWithSource> flows = this.flowListeners.flows()
            .stream()
            .collect(Collectors.toMap(FlowWithSource::uidWithoutRevision, Function.identity()));

        return this.triggerState.findAllForAllTenants().stream()
            .filter(trigger -> flows.containsKey(trigger.flowUid()))
            .map(trigger ->
                new FlowWithTriggers(
                    flows.get(trigger.flowUid()),
                    ListUtils.emptyOnNull(flows.get(trigger.flowUid()).getTriggers()).stream().filter(t -> t.getId().equals(trigger.getTriggerId())).findFirst().orElse(null),
                    trigger,
                    null,
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

        this.handleNext(this.flowListeners.flows(), now, (triggers, scheduleContext) -> {
            if (triggers.isEmpty()) {
                return;
            }

            List<Trigger> triggerContextsToEvaluate = triggers.stream()
                .filter(trigger -> Boolean.FALSE.equals(trigger.getDisabled()))
                .toList();

            List<FlowWithTriggers> schedulable = this.computeSchedulable(flowListeners.flows(), triggerContextsToEvaluate, scheduleContext);

            metricRegistry
                .counter(MetricRegistry.SCHEDULER_LOOP_COUNT)
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
                    .workerTrigger((WorkerTriggerInterface) flowWithTriggers.getAbstractTrigger())
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
                .filter(this::isExecutionNotRunning)
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
                .counter(MetricRegistry.SCHEDULER_EVALUATE_COUNT)
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
                                    this.triggerState.save(triggerRunning, scheduleContext);
                                    this.sendWorkerTriggerToWorker(flowWithTrigger);
                                } catch (InternalException e) {
                                    logService.logTrigger(
                                        f.getTriggerContext(),
                                        logger,
                                        Level.ERROR,
                                        "Unable to send worker trigger to worker",
                                        e
                                    );
                                }
                            } else if (f.getWorkerTrigger() instanceof Schedulable schedule) {
                                // This is the Schedule, all other triggers should have an interval.
                                // So we evaluate it now as there is no need to send it to the worker.
                                // Schedule didn't use the triggerState to allow backfill.
                                Optional<SchedulerExecutionWithTrigger> schedulerExecutionWithTrigger = evaluateScheduleTrigger(f);
                                if (schedulerExecutionWithTrigger.isPresent()) {
                                    this.handleEvaluateSchedulingTriggerResult(schedule, schedulerExecutionWithTrigger.get(), f.getConditionContext(), scheduleContext);
                                } else {
                                    // compute next date and save the trigger to avoid evaluating it each second
                                    Trigger trigger = Trigger.fromEvaluateFailed(
                                        f.getTriggerContext(),
                                        schedule.nextEvaluationDate(f.getConditionContext(), Optional.of(f.getTriggerContext()))
                                    );
                                    trigger = trigger.checkBackfill();
                                    this.triggerState.save(trigger, scheduleContext);
                                }
                            } else {
                                logService.logTrigger(
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
                            } catch (Exception e) {
                                logError(f, e);
                            }
                            var trigger = f.getTriggerContext().toBuilder().nextExecutionDate(nextExecutionDate).build().checkBackfill();
                            this.triggerState.save(trigger, scheduleContext);
                        }
                    } catch (Exception ie) {
                        // validate schedule condition can fail to render variables
                        // in this case, we send a failed execution so the trigger is not evaluated each second.
                        logger.error("Unable to evaluate the trigger '{}'", f.getAbstractTrigger().getId(), ie);
                        Execution execution = Execution.builder()
                            .id(IdUtils.create())
                            .tenantId(f.getTriggerContext().getTenantId())
                            .namespace(f.getTriggerContext().getNamespace())
                            .flowId(f.getTriggerContext().getFlowId())
                            .flowRevision(f.getFlow().getRevision())
                            .labels(LabelService.labelsExcludingSystem(f.getFlow()))
                            .state(new State().withState(State.Type.FAILED))
                            .build();
                        ZonedDateTime nextExecutionDate = this.nextEvaluationDate(f.getAbstractTrigger());
                        var trigger = f.getTriggerContext().resetExecution(State.Type.FAILED, nextExecutionDate);
                        this.saveLastTriggerAndEmitExecution(execution, trigger, triggerToSave -> this.triggerState.save(triggerToSave, scheduleContext));
                    }
                });
        });
    }

    private void handleEvaluateWorkerTriggerResult(SchedulerExecutionWithTrigger result, ZonedDateTime nextExecutionDate) {
        Optional.ofNullable(result)
            .ifPresent(executionWithTrigger -> {
                    log(executionWithTrigger);

                    Trigger trigger = Trigger.of(
                        executionWithTrigger.getTriggerContext(),
                        executionWithTrigger.getExecution(),
                        nextExecutionDate
                    );

                    // Worker triggers result is evaluated in another thread with the workerTriggerResultQueue.
                    // We can then update the trigger directly.
                    this.saveLastTriggerAndEmitExecution(executionWithTrigger.getExecution(), trigger, triggerToSave -> this.triggerState.update(triggerToSave));
                }
            );
    }

    private void handleEvaluateSchedulingTriggerResult(Schedulable schedule, SchedulerExecutionWithTrigger result, ConditionContext conditionContext, ScheduleContextInterface scheduleContext) throws Exception {
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

        // Schedule triggers are being executed directly from the handle method within the context where triggers are locked.
        // So we must save them by passing the scheduleContext.
        this.saveLastTriggerAndEmitExecution(result.getExecution(), trigger, triggerToSave -> this.triggerState.save(triggerToSave, scheduleContext));
    }

    protected void saveLastTriggerAndEmitExecution(Execution execution, Trigger trigger, Consumer<Trigger> saveAction) {
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
                Execution failedExecution = newExecution.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED);
                this.executionQueue.emit(failedExecution);
                this.executionEventPublisher.publishEvent(new CrudEvent<>(failedExecution, CrudEventType.CREATE));
            } catch (QueueException ex) {
                log.error("Unable to emit the execution", ex);
            }
        }
    }

    private boolean isExecutionNotRunning(FlowWithWorkerTrigger f) {
        Trigger lastTrigger = f.getTriggerContext();

        if (lastTrigger.getExecutionId() == null) {
            return true;
        }

        Optional<Execution> execution = executionState.findById(lastTrigger.getTenantId(), lastTrigger.getExecutionId());

        // executionState hasn't received the execution, we skip
        if (execution.isEmpty()) {
            if (lastTrigger.getUpdatedDate() != null) {
                metricRegistry
                    .timer(MetricRegistry.SCHEDULER_EXECUTION_MISSING_DURATION, metricRegistry.tags(lastTrigger))
                    .record(Duration.between(lastTrigger.getUpdatedDate(), Instant.now()));
            }

            if (lastTrigger.getUpdatedDate() == null || lastTrigger.getUpdatedDate().plusSeconds(60).isBefore(Instant.now())) {
                logService.logTrigger(
                    f.getTriggerContext(),
                    log,
                    Level.WARN,
                    "Execution '{}' is not found, schedule is blocked since '{}'",
                    lastTrigger.getExecutionId(),
                    lastTrigger.getUpdatedDate()
                );
            }

            return false;
        }

        if (lastTrigger.getUpdatedDate() != null) {
            metricRegistry
                .timer(MetricRegistry.SCHEDULER_EXECUTION_RUNNING_DURATION, metricRegistry.tags(lastTrigger))
                .record(Duration.between(lastTrigger.getUpdatedDate(), Instant.now()));
        }

        if (log.isDebugEnabled()) {
            logService.logTrigger(
                f.getTriggerContext(),
                log,
                Level.DEBUG,
                "Execution '{}' is still '{}', updated at '{}'",
                lastTrigger.getExecutionId(),
                execution.get().getState().getCurrent(),
                lastTrigger.getUpdatedDate()
            );
        }

        return false;
    }

    private void log(SchedulerExecutionWithTrigger executionWithTrigger) {
        metricRegistry
            .counter(MetricRegistry.SCHEDULER_TRIGGER_COUNT, metricRegistry.tags(executionWithTrigger))
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
                    .timer(MetricRegistry.SCHEDULER_TRIGGER_DELAY_DURATION, metricRegistry.tags(executionWithTrigger))
                    .record(Duration.between(
                        executionWithTrigger.getTriggerContext().getDate(), now
                    ));
            }
        }

        logService.logTrigger(
            executionWithTrigger.getTriggerContext(),
            log,
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
        try {
            FlowWithWorkerTrigger flowWithWorkerTrigger = flowWithTrigger.from(pluginDefaultService.injectDefaults(
                flowWithTrigger.getFlow(),
                flowWithTrigger.getConditionContext().getRunContext().logger()
            ));

            // mutability dirty hack that forces the creation of a new triggerExecutionId
            DefaultRunContext runContext = (DefaultRunContext) flowWithWorkerTrigger.getConditionContext().getRunContext();
            runContextInitializer.forScheduler(
                runContext,
                flowWithWorkerTrigger.getTriggerContext(),
                flowWithWorkerTrigger.getAbstractTrigger()
            );

            Optional<Execution> evaluate = ((Schedulable) flowWithWorkerTrigger.getWorkerTrigger()).evaluate(
                flowWithWorkerTrigger.getConditionContext(),
                flowWithWorkerTrigger.getTriggerContext()
            );

            if (log.isDebugEnabled()) {
                logService.logTrigger(
                    flowWithWorkerTrigger.getTriggerContext(),
                    log,
                    Level.DEBUG,
                    "[type: {}] {}",
                    flowWithWorkerTrigger.getAbstractTrigger().getType(),
                    evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
                );
            }

            flowWithWorkerTrigger.getConditionContext().getRunContext().cleanup();

            return evaluate.map(execution -> new SchedulerExecutionWithTrigger(
                execution,
                flowWithTrigger.getTriggerContext()
            ));
        } catch (Exception e) {
            logError(flowWithTrigger, e);
            return Optional.empty();
        }
    }

    private void logError(FlowWithWorkerTrigger flowWithWorkerTriggerNextDate, Throwable e) {
        Logger logger = flowWithWorkerTriggerNextDate.getConditionContext().getRunContext().logger();

        logService.logTrigger(
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

    private void logError(ConditionContext conditionContext, FlowWithSource flow, AbstractTrigger trigger, Throwable e) {
        Logger logger = conditionContext.getRunContext().logger();

        logService.logFlow(
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
        FlowWithWorkerTrigger flowWithTriggerWithDefault = flowWithTrigger.from(
            pluginDefaultService.injectDefaults(flowWithTrigger.getFlow(),
                flowWithTrigger.getConditionContext().getRunContext().logger())
        );

        if (log.isDebugEnabled()) {
            logService.logTrigger(
                flowWithTrigger.getTriggerContext(),
                log,
                Level.DEBUG,
                "[date: {}] Scheduling evaluation to the worker",
                flowWithTrigger.getTriggerContext().getDate()
            );
        }

        var workerTrigger = WorkerTrigger
            .builder()
            .trigger(flowWithTriggerWithDefault.abstractTrigger)
            .triggerContext(flowWithTriggerWithDefault.triggerContext)
            .conditionContext(flowWithTriggerWithDefault.conditionContext)
            .build();
        try {
            this.workerTaskQueue.emit(workerGroupService.resolveGroupFromJob(workerTrigger).map(group -> group.getKey()).orElse(null), workerTrigger);
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
            try {
                if (onClose != null) {
                    onClose.run();
                }
            } catch (Exception e) {
                log.error("Unexpected error while terminating scheduler.", e);
            }
            this.receiveCancellations.forEach(Runnable::run);
            this.scheduleExecutor.shutdown();
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
        private WorkerTriggerInterface workerTrigger;
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
                .workerTrigger((WorkerTriggerInterface) abstractTrigger)
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
                .workerTrigger(f.getWorkerTrigger())
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
        private final RunContext runContext;
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
