package io.kestra.core.schedulers;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Singleton
public abstract class AbstractScheduler implements Scheduler {
    protected final ApplicationContext applicationContext;
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final QueueInterface<WorkerJob> workerTaskQueue;
    private final WorkerTriggerResultQueueInterface workerTriggerResultQueue;
    protected final FlowListenersInterface flowListeners;
    private final RunContextFactory runContextFactory;
    private final MetricRegistry metricRegistry;
    private final ConditionService conditionService;
    private final TaskDefaultService taskDefaultService;
    private final WorkerGroupService workerGroupService;
    private final LogService logService;

    // must be volatile as it's updated by the flow listener thread and read by the scheduleExecutor thread
    private volatile Boolean isReady = false;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();

    protected SchedulerTriggerStateInterface triggerState;

    // schedulable and schedulableNextDate must be volatile and their access synchronized as they are updated and read by different threads.
    @Getter
    private volatile List<FlowWithTriggers> schedulable = new ArrayList<>();
    @Getter
    private volatile Map<String, FlowWithPollingTriggerNextDate> schedulableNextDate = new ConcurrentHashMap<>();

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
        this.workerTriggerResultQueue = applicationContext.getBean(WorkerTriggerResultQueueInterface.class);
        this.flowListeners = flowListeners;
        this.runContextFactory = applicationContext.getBean(RunContextFactory.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.taskDefaultService = applicationContext.getBean(TaskDefaultService.class);
        this.workerGroupService = applicationContext.getBean(WorkerGroupService.class);
        this.logService = applicationContext.getBean(LogService.class);
    }

    protected boolean isReady() {
        return isReady;
    }

    @Override
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
        Thread thread = new Thread(
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
            },
            "scheduler-listener"
        );
        thread.start();

        // remove trigger on flow update & update local triggers store
        this.flowListeners.listen((flow, previous) -> {
            if (flow.isDeleted() || previous != null) {
                List<AbstractTrigger> triggersDeleted = flow.isDeleted() ? ListUtils.emptyOnNull(flow.getTriggers()) : FlowService
                    .findRemovedTrigger(flow, previous);
                triggersDeleted.forEach(abstractTrigger -> {
                    Trigger trigger = Trigger.of(flow, abstractTrigger);
                    this.triggerQueue.delete(trigger);
                });
            }
            if (previous != null) {
                FlowService.findUpdatedTrigger(flow, previous)
                    .forEach(abstractTrigger -> {
                        if (abstractTrigger instanceof PollingTriggerInterface) {
                            RunContext runContext = runContextFactory.of(flow, abstractTrigger);
                            ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                            try {
                                this.triggerState.update(flow, abstractTrigger, conditionContext);
                            } catch (Exception e) {
                                logError(conditionContext, flow, abstractTrigger, e);
                            }
                        }
                    });
            }
        });

        // listen to WorkerTriggerResult from polling triggers
        this.workerTriggerResultQueue.receive(
            null,
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker trigger result: {}", either.getRight().getMessage());

                    return;
                }

                WorkerTriggerResult workerTriggerResult = either.getLeft();
                if (workerTriggerResult.getSuccess() && workerTriggerResult.getExecution().isPresent()) {
                    SchedulerExecutionWithTrigger triggerExecution = new SchedulerExecutionWithTrigger(
                        workerTriggerResult.getExecution().get(),
                        workerTriggerResult.getTriggerContext()
                    );
                    ZonedDateTime nextExecutionDate = ((PollingTriggerInterface) workerTriggerResult.getTrigger()).nextEvaluationDate();
                    this.handleEvaluatePollingTriggerResult(triggerExecution, nextExecutionDate);
                } else {
                    ZonedDateTime nextExecutionDate = ((PollingTriggerInterface) workerTriggerResult.getTrigger()).nextEvaluationDate();
                    this.triggerState.update(Trigger.of(workerTriggerResult.getTriggerContext(), nextExecutionDate));
                }
            }
        );
    }

    // Initialized local trigger state,
    // and if some flows were created outside the box, for example from the CLI,
    // then we may have some triggers that are not created yet.
    private void initializedTriggers(List<Flow> flows) {
        record FlowAndTrigger(Flow flow, AbstractTrigger trigger) {}
        List<Trigger> triggers = triggerState.findAllForAllTenants();

        flows
            .stream()
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .flatMap(flow -> flow.getTriggers().stream().filter(trigger -> trigger instanceof PollingTriggerInterface).map(trigger -> new FlowAndTrigger(flow, trigger)))
            .forEach(flowAndTrigger -> {
                Optional<Trigger> trigger = triggers.stream().filter(t -> t.uid().equals(Trigger.uid(flowAndTrigger.flow(), flowAndTrigger.trigger()))).findFirst(); // must have one or none
                if (trigger.isEmpty()) {
                    RunContext runContext = runContextFactory.of(flowAndTrigger.flow(), flowAndTrigger.trigger());
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flowAndTrigger.flow(), null);
                    try {
                        // new polling triggers will be evaluated immediately except schedule that will be evaluated at the next cron schedule
                        ZonedDateTime nextExecutionDate = flowAndTrigger.trigger() instanceof Schedule  schedule ? schedule.nextEvaluationDate(conditionContext, Optional.empty()): now();
                        Trigger newTrigger = Trigger.builder()
                            .tenantId(flowAndTrigger.flow().getTenantId())
                            .namespace(flowAndTrigger.flow().getNamespace())
                            .flowId(flowAndTrigger.flow().getId())
                            .flowRevision(flowAndTrigger.flow().getRevision())
                            .triggerId(flowAndTrigger.trigger().getId())
                            .date(now())
                            .nextExecutionDate(nextExecutionDate)
                            .stopAfter(flowAndTrigger.trigger().getStopAfter())
                            .build();
                        this.triggerState.create(newTrigger);
                    } catch (Exception e) {
                        logError(conditionContext, flowAndTrigger.flow(), flowAndTrigger.trigger(), e);
                    }
                } else if (flowAndTrigger.trigger() instanceof Schedule schedule) {
                    // we recompute the Schedule nextExecutionDate if needed
                    RunContext runContext = runContextFactory.of(flowAndTrigger.flow(), flowAndTrigger.trigger());
                    Schedule.RecoverMissedSchedules recoverMissedSchedules = Optional.ofNullable(schedule.getRecoverMissedSchedules()).orElseGet(() -> schedule.defaultRecoverMissedSchedules(runContext));
                    if (recoverMissedSchedules == Schedule.RecoverMissedSchedules.LAST) {
                        ConditionContext conditionContext = conditionService.conditionContext(runContext, flowAndTrigger.flow(), null);
                        ZonedDateTime previousDate = schedule.previousEvaluationDate(conditionContext);
                        if (previousDate.isAfter(trigger.get().getDate())) {
                            Trigger updated = trigger.get().toBuilder().nextExecutionDate(previousDate).build();
                            this.triggerState.update(updated);
                        }
                    } else if (recoverMissedSchedules == Schedule.RecoverMissedSchedules.NONE ) {
                        Trigger updated = trigger.get().toBuilder().nextExecutionDate(schedule.nextEvaluationDate()).build();
                        this.triggerState.update(updated);
                    }
                }
            });

        this.isReady = true;
    }

    private List<FlowWithTriggers> computeSchedulable(List<Flow> flows, List<Trigger> triggerContextsToEvaluate, ScheduleContextInterface scheduleContext) {
        List<String> flowToKeep = triggerContextsToEvaluate.stream().map(Trigger::getFlowId).toList();

        return flows
            .stream()
            .filter(flow -> flowToKeep.contains(flow.getId()))
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .filter(flow -> !flow.isDisabled() && !(flow instanceof FlowWithException))
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled() && abstractTrigger instanceof PollingTriggerInterface)
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
                                .nextExecutionDate(((PollingTriggerInterface) abstractTrigger).nextEvaluationDate(conditionContext, Optional.of(lastTrigger)))
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

    abstract public void handleNext(List<Flow> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer);

    private void handle() {
        if (!isReady()) {
            log.warn("Scheduler is not ready, waiting");
            return;
        }

        ZonedDateTime now = now();

        this.handleNext(this.flowListeners.flows(), now, (triggers, scheduleContext) -> {

            if (triggers.isEmpty()) {
                return;
            }

            triggers.forEach(trigger -> schedulableNextDate.remove(trigger.uid()));
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
            List<FlowWithPollingTriggerNextDate> readyForEvaluate = schedulable
                .stream()
                .map(flowWithTriggers -> FlowWithPollingTrigger.builder()
                    .flow(flowWithTriggers.getFlow())
                    .abstractTrigger(flowWithTriggers.getAbstractTrigger())
                    .pollingTrigger((PollingTriggerInterface) flowWithTriggers.getAbstractTrigger())
                    .conditionContext(flowWithTriggers.getConditionContext())
                    .triggerContext(flowWithTriggers.TriggerContext.toBuilder().date(now()).stopAfter(flowWithTriggers.getAbstractTrigger().getStopAfter()).build())
                    .build())
                .filter(f -> f.getTriggerContext().getEvaluateRunningDate() == null)
                .filter(this::isExecutionNotRunning)
                .map(FlowWithPollingTriggerNextDate::of)
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

                            if (f.getPollingTrigger().getInterval() != null) {
                                // If it has an interval, the Worker will execute the trigger.
                                // Normally, only the Schedule trigger has no interval.
                                Trigger triggerRunning = Trigger.of(f.getTriggerContext(), now);

                                try {
                                    this.triggerState.save(triggerRunning, scheduleContext);
                                    this.sendPollingTriggerToWorker(f);
                                } catch (InternalException e) {
                                    logService.logTrigger(
                                        f.getTriggerContext(),
                                        logger,
                                        Level.ERROR,
                                        "Unable to send polling trigger to worker",
                                        e
                                    );
                                }
                            }
                            else if (f.getPollingTrigger() instanceof Schedule schedule) {
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
                                    "Polling trigger must have an interval (except the Schedule)"
                                );
                            }
                        } else {
                            ZonedDateTime nextExecutionDate = null;
                            try {
                                nextExecutionDate = f.getPollingTrigger().nextEvaluationDate(f.getConditionContext(), Optional.of(f.getTriggerContext()));
                            } catch (Exception e) {
                                logError(f, e);
                            }
                            var trigger = f.getTriggerContext().toBuilder().nextExecutionDate(nextExecutionDate).build().checkBackfill();
                            this.triggerState.save(trigger, scheduleContext);
                        }
                    } catch (InternalException ie) {
                        // validate schedule condition can fail to render variables
                        // in this case, we send a failed execution so the trigger is not evaluated each second.
                        logger.error("Unable to evaluate the trigger '{}'", f.getAbstractTrigger().getId(), ie);
                        Execution execution = Execution.builder()
                            .id(IdUtils.create())
                            .tenantId(f.getTriggerContext().getTenantId())
                            .namespace(f.getTriggerContext().getNamespace())
                            .flowId(f.getTriggerContext().getFlowId())
                            .flowRevision(f.getTriggerContext().getFlowRevision())
                            .labels(f.getFlow().getLabels())
                            .state(new State().withState(State.Type.FAILED))
                            .build();
                        ZonedDateTime nextExecutionDate = f.getPollingTrigger().nextEvaluationDate();
                        var trigger = f.getTriggerContext().resetExecution(State.Type.FAILED, nextExecutionDate);
                        this.saveLastTriggerAndEmitExecution(execution, trigger, triggerToSave -> this.triggerState.save(triggerToSave, scheduleContext));
                    }
                });
        });
    }

    private void handleEvaluatePollingTriggerResult(SchedulerExecutionWithTrigger result, ZonedDateTime nextExecutionDate) {
        Stream.of(result)
            .filter(Objects::nonNull)
            .peek(this::log)
            .forEach(executionWithTrigger -> {
                    Trigger trigger = Trigger.of(
                        executionWithTrigger.getTriggerContext(),
                        executionWithTrigger.getExecution(),
                        nextExecutionDate
                    );

                    // Polling triggers result is evaluated in another thread with the workerTriggerResultQueue.
                    // We can then update the trigger directly.
                    this.saveLastTriggerAndEmitExecution(executionWithTrigger.getExecution(), trigger, triggerToSave -> this.triggerState.update(triggerToSave));
                }
            );
    }

    private void handleEvaluateSchedulingTriggerResult(Schedule schedule, SchedulerExecutionWithTrigger result, ConditionContext conditionContext, ScheduleContextInterface scheduleContext) {
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

        // we need to be sure that the tenantId is propagated from the trigger to the execution
        var newExecution = execution.withTenantId(trigger.getTenantId());
        this.executionQueue.emit(newExecution);
    }

    private boolean isExecutionNotRunning(FlowWithPollingTrigger f) {
        Trigger lastTrigger = f.getTriggerContext();

        if (lastTrigger.getExecutionId() == null) {
            return true;
        }

        // The execution is not yet started, we skip
        if (lastTrigger.getExecutionCurrentState() == null) {
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
                lastTrigger.getExecutionCurrentState(),
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

    private Optional<SchedulerExecutionWithTrigger> evaluateScheduleTrigger(FlowWithPollingTrigger flowWithTrigger) {
        try {
            FlowWithPollingTrigger flowWithPollingTrigger = flowWithTrigger.from(taskDefaultService.injectDefaults(
                flowWithTrigger.getFlow(),
                flowWithTrigger.getConditionContext().getRunContext().logger()
            ));

            // mutability dirty hack that forces the creation of a new triggerExecutionId
            flowWithPollingTrigger.getConditionContext().getRunContext().forScheduler(
                flowWithPollingTrigger.getTriggerContext(),
                flowWithTrigger.getAbstractTrigger()
            );

            Optional<Execution> evaluate = flowWithPollingTrigger.getPollingTrigger().evaluate(
                flowWithPollingTrigger.getConditionContext(),
                flowWithPollingTrigger.getTriggerContext()
            );

            if (log.isDebugEnabled()) {
                logService.logTrigger(
                    flowWithPollingTrigger.getTriggerContext(),
                    log,
                    Level.DEBUG,
                    "[type: {}] {}",
                    flowWithPollingTrigger.getAbstractTrigger().getType(),
                    evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
                );
            }

            flowWithPollingTrigger.getConditionContext().getRunContext().cleanup();

            return evaluate.map(execution -> new SchedulerExecutionWithTrigger(
                execution,
                flowWithTrigger.getTriggerContext()
            ));
        } catch (Exception e) {
            logError(flowWithTrigger, e);
            return Optional.empty();
        }
    }

    private void logError(FlowWithPollingTrigger flowWithPollingTriggerNextDate, Throwable e) {
        Logger logger = flowWithPollingTriggerNextDate.getConditionContext().getRunContext().logger();

        logService.logTrigger(
            flowWithPollingTriggerNextDate.getTriggerContext(),
            logger,
            Level.WARN,
            "[date: {}] Evaluate Failed with error '{}'",
            flowWithPollingTriggerNextDate.getTriggerContext().getDate(),
            e.getMessage(),
            e
        );

        if (logger.isTraceEnabled()) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }

    private void logError(ConditionContext conditionContext, Flow flow, AbstractTrigger trigger, Throwable e) {
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

    private void sendPollingTriggerToWorker(FlowWithPollingTrigger flowWithTrigger) throws InternalException {
        FlowWithPollingTrigger flowWithTriggerWithDefault = flowWithTrigger.from(
            taskDefaultService.injectDefaults(flowWithTrigger.getFlow(),
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
        this.workerTaskQueue.emit(workerGroupService.resolveGroupFromJob(workerTrigger), workerTrigger);
    }

    @Override
    @PreDestroy
    public void close() {
        this.scheduleExecutor.shutdown();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    private static class FlowWithPollingTrigger {
        private Flow flow;
        private AbstractTrigger abstractTrigger;
        private PollingTriggerInterface pollingTrigger;
        private Trigger triggerContext;
        private ConditionContext conditionContext;

        public FlowWithPollingTrigger from(Flow flow) throws InternalException {
            AbstractTrigger abstractTrigger = flow.getTriggers()
                .stream()
                .filter(a -> a.getId().equals(this.abstractTrigger.getId()) && a instanceof PollingTriggerInterface)
                .findFirst()
                .orElseThrow(() -> new InternalException("Couldn't find the trigger '" + this.abstractTrigger.getId() + "' on flow '" + flow.uid() + "'"));

            return this.toBuilder()
                .flow(flow)
                .abstractTrigger(abstractTrigger)
                .pollingTrigger((PollingTriggerInterface) abstractTrigger)
                .build();
        }
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class FlowWithPollingTriggerNextDate extends FlowWithPollingTrigger {
        private ZonedDateTime next;

        private static FlowWithPollingTriggerNextDate of(FlowWithPollingTrigger f) {
            return FlowWithPollingTriggerNextDate.builder()
                .flow(f.getFlow())
                .abstractTrigger(f.getAbstractTrigger())
                .pollingTrigger(f.getPollingTrigger())
                .conditionContext(f.getConditionContext())
                .triggerContext(Trigger.builder()
                    .tenantId(f.getTriggerContext().getTenantId())
                    .namespace(f.getTriggerContext().getNamespace())
                    .flowId(f.getTriggerContext().getFlowId())
                    .flowRevision(f.getTriggerContext().getFlowRevision())
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
        private final Flow flow;
        private final AbstractTrigger AbstractTrigger;
        private final Trigger TriggerContext;
        private final RunContext runContext;
        private final ConditionContext conditionContext;

        public String uid() {
            return Trigger.uid(flow, AbstractTrigger);
        }
    }
}
