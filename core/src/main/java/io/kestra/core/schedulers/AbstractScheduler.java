package io.kestra.core.schedulers;

import com.google.common.base.Throwables;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
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
    protected Boolean isReady = false;

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
                    // previously, if no interval the trigger was executed immediately. I think only the Schedule trigger has no interval
                    // now that all triggers are sent to the worker, we need to do this to avoid issues with backfills
                    // as the same trigger will be evaluated multiple-time which is not supported by the 'addToRunning' method
                    // if the trigger didn't trigger an execution, we will need to clear the evaluation lock
                    // TODO now that Schedule is executed by the Scheduler this could be relaxed
                    if (workerTriggerResult.getTrigger() instanceof PollingTriggerInterface &&
                        ((PollingTriggerInterface) workerTriggerResult.getTrigger()).getInterval() != null) {

                        ZonedDateTime nextExecutionDate = ((PollingTriggerInterface) workerTriggerResult.getTrigger()).nextEvaluationDate();
                        this.triggerState.update(Trigger.of(workerTriggerResult.getTriggerContext(), nextExecutionDate));
                    }
                }
            }
        );
    }

    // Initialized local trigger state
    // and if some flows were created outside the box, for example from CLI
    // then we may have some triggers that are not created yet
    private void initializedTriggers(List<Flow> flows) {
        List<Trigger> triggers = triggerState.findAllForAllTenants();

        flows.forEach(flow -> {
            ListUtils.emptyOnNull(flow.getTriggers()).forEach(abstractTrigger -> {
                if (triggers.stream().noneMatch(trigger -> trigger.uid().equals(Trigger.uid(flow, abstractTrigger))) && abstractTrigger instanceof PollingTriggerInterface pollingAbstractTrigger) {
                    RunContext runContext = runContextFactory.of(flow, abstractTrigger);
                    ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
                    try {
                        // new polling triggers will be evaluated immediately except schedule that will be evaluated at the next cron schedule
                        ZonedDateTime nextExecutionDate = pollingAbstractTrigger instanceof Schedule ? pollingAbstractTrigger.nextEvaluationDate(conditionContext, Optional.empty()): now();
                        Trigger newTrigger = Trigger.builder()
                            .tenantId(flow.getTenantId())
                            .namespace(flow.getNamespace())
                            .flowId(flow.getId())
                            .flowRevision(flow.getRevision())
                            .triggerId(abstractTrigger.getId())
                            .date(now())
                            .nextExecutionDate(nextExecutionDate)
                            .stopAfter(abstractTrigger.getStopAfter())
                            .build();
                        this.triggerState.create(newTrigger);
                    } catch (Exception e) {
                        logError(conditionContext, flow, abstractTrigger, e);
                    }
                }
            });
        });
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
                    try {
                        Trigger lastTrigger = triggerContextsToEvaluate
                            .stream()
                            .filter(triggerContextToFind -> triggerContextToFind.uid().equals(Trigger.uid(flow, abstractTrigger)))
                            .findFirst()
                            .orElse(null);
                        // If trigger is not found in triggers to evaluate, then we ignore it
                        if (lastTrigger == null) {
                            return null;
                            // Backwards compatibility: we add a next execution date that we compute, this avoid re-triggering all existing trigger
                        } else if (lastTrigger.getNextExecutionDate() == null) {
                            triggerContext = lastTrigger.toBuilder()
                                .nextExecutionDate(((PollingTriggerInterface) abstractTrigger).nextEvaluationDate(conditionContext, Optional.of(lastTrigger)))
                                .build();
                            this.triggerState.save(triggerContext, scheduleContext);
                        } else {
                            triggerContext = lastTrigger;
                        }
                    } catch (Exception e) {
                        logError(conditionContext, flow, abstractTrigger, e);
                        return null;
                    }
                    return new FlowWithTriggers(
                        flow,
                        abstractTrigger,
                        triggerContext,
                        runContext,
                        conditionService.conditionContext(runContext, flow, null)
                    );
                })
            )
            .filter(Objects::nonNull).toList();
    }

    abstract public void handleNext(List<Flow> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer);

    private void handle() {
        if (!this.isReady) {
            log.warn("Scheduler is not ready, waiting");
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
                .filter(f -> conditionService.isValid(f.getFlow(), f.getAbstractTrigger(), f.getConditionContext()))
                .map(flowWithTriggers -> FlowWithPollingTrigger.builder()
                    .flow(flowWithTriggers.getFlow())
                    .abstractTrigger(flowWithTriggers.getAbstractTrigger())
                    .pollingTrigger((PollingTriggerInterface) flowWithTriggers.getAbstractTrigger())
                    .conditionContext(flowWithTriggers.getConditionContext())
                    .triggerContext(flowWithTriggers.TriggerContext.toBuilder().date(now()).stopAfter(flowWithTriggers.getAbstractTrigger().getStopAfter()).build())
                    .build())
                .filter(f -> f.getTriggerContext().getEvaluateRunningDate() == null && !f.getTriggerContext().getDisabled())
                .filter(this::isExecutionNotRunning)
                .map(f -> {
                    try {

                        return FlowWithPollingTriggerNextDate.of(f);
                    } catch (Exception e) {
                        logError(f, e);

                        return null;
                    }
                })
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

                    if (f.getPollingTrigger().getInterval() != null) {
                        // If have an interval, the trigger is executed by the Worker.
                        // Normally, only the Schedule trigger has no interval.
                        Trigger triggerRunning = Trigger.of(f.getTriggerContext(), now);

                        try {
                            this.triggerState.save(triggerRunning, scheduleContext);
                            this.sendPollingTriggerToWorker(f);
                        } catch (InternalException e) {
                            logger.error(
                                "[namespace: {}] [flow: {}] [trigger: {}] Unable to send polling trigger to worker",
                                f.getFlow().getNamespace(),
                                f.getFlow().getId(),
                                f.getAbstractTrigger().getId(),
                                e
                            );
                        }
                    } else if (f.getPollingTrigger() instanceof Schedule) {
                        // This is the Schedule, all other triggers should have an interval.
                        // So we evaluate it now as there is no need to send it to the worker.
                        // Schedule didn't use the triggerState to allow backfill.
                        try {
                            SchedulerExecutionWithTrigger schedulerExecutionWithTrigger = evaluateScheduleTrigger(f);
                            this.handleEvaluateSchedulingTriggerResult(schedulerExecutionWithTrigger, scheduleContext);
                        } catch (Exception e) {
                            logger.error(
                                "[namespace: {}] [flow: {}] [trigger: {}] Evaluate schedule trigger failed",
                                f.getFlow().getNamespace(),
                                f.getFlow().getId(),
                                f.getAbstractTrigger().getId(),
                                e
                            );
                        }
                    } else {
                        logger.error(
                            "[namespace: {}] [flow: {}] [trigger: {}] Polling trigger must have an interval (except the Schedule)",
                            f.getFlow().getNamespace(),
                            f.getFlow().getId(),
                            f.getAbstractTrigger().getId()
                        );
                    }
                });

        });
    }

    // Polling triggers result is evaluated in another thread
    // with the workerTriggerResultQueue,
    // so we can't save them now
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

                    // Check if the localTriggerState contains it
                    // however, its mean it has been deleted during the execution time
                    this.triggerState.update(trigger);
                    this.saveLastTriggerAndEmitExecution(executionWithTrigger, trigger);
                }
            );
    }

    // Schedule triggers are being executed directly from
    // the handle method within the context where triggers are locked,
    // so we can save it now by pass the context
    private void handleEvaluateSchedulingTriggerResult(SchedulerExecutionWithTrigger result, ScheduleContextInterface scheduleContext) {
        Stream.of(result)
            .filter(Objects::nonNull)
            .peek(this::log)
            .forEach(executionWithTrigger -> {
                    Trigger trigger = Trigger.of(
                        executionWithTrigger.getTriggerContext(),
                        executionWithTrigger.getExecution(),
                        ZonedDateTime.parse((String) executionWithTrigger.getExecution().getTrigger().getVariables().get("next"))
                    );
                    trigger = trigger.checkBackfill();
                    this.triggerState.save(trigger, scheduleContext);
                    this.saveLastTriggerAndEmitExecution(executionWithTrigger, trigger);
                }
            );
    }

    protected void saveLastTriggerAndEmitExecution(SchedulerExecutionWithTrigger executionWithTrigger, Trigger trigger) {
        // we need to be sure that the tenantId is propagated from the trigger to the execution
        var execution = executionWithTrigger.getExecution().withTenantId(executionWithTrigger.getTriggerContext().getTenantId());
        this.executionQueue.emit(execution);
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
                log.warn(
                    "[namespace: {}] [flow: {}] [trigger: {}] Execution '{}' is not found, schedule is blocked since '{}'",
                    lastTrigger.getNamespace(),
                    lastTrigger.getFlowId(),
                    lastTrigger.getTriggerId(),
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
            log.debug(
                "[namespace: {}] [flow: {}] [trigger: {}] Execution '{}' is still '{}', updated at '{}'",
                lastTrigger.getNamespace(),
                lastTrigger.getFlowId(),
                lastTrigger.getTriggerId(),
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

        log.info(
            "[namespace: {}] [flow: {}] [trigger: {}] Scheduled execution {} at '{}' started at '{}'",
            executionWithTrigger.getExecution().getNamespace(),
            executionWithTrigger.getExecution().getFlowId(),
            executionWithTrigger.getTriggerContext().getTriggerId(),
            executionWithTrigger.getExecution().getId(),
            executionWithTrigger.getTriggerContext().getDate(),
            now
        );
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private SchedulerExecutionWithTrigger evaluateScheduleTrigger(FlowWithPollingTrigger flowWithTrigger) {
        try {
            FlowWithPollingTrigger flowWithPollingTrigger = flowWithTrigger.from(taskDefaultService.injectDefaults(
                flowWithTrigger.getFlow(),
                flowWithTrigger.getConditionContext().getRunContext().logger()
            ));

            // @TODO: mutability dirty that force creation of a new triggerExecutionId
            flowWithPollingTrigger.getConditionContext().getRunContext().forScheduler(
                flowWithPollingTrigger.getTriggerContext(),
                flowWithTrigger.getAbstractTrigger()
            );

            Optional<Execution> evaluate = flowWithPollingTrigger.getPollingTrigger().evaluate(
                flowWithPollingTrigger.getConditionContext(),
                flowWithPollingTrigger.getTriggerContext()
            );

            if (log.isDebugEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "[namespace: {}] [flow: {}] [trigger: {}] [type: {}] {}",
                        flowWithPollingTrigger.getFlow().getNamespace(),
                        flowWithPollingTrigger.getFlow().getId(),
                        flowWithPollingTrigger.getAbstractTrigger().getId(),
                        flowWithPollingTrigger.getAbstractTrigger().getType(),
                        evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
                    );
                }
            }

            flowWithPollingTrigger.getConditionContext().getRunContext().cleanup();

            return evaluate.map(execution -> new SchedulerExecutionWithTrigger(
                execution,
                flowWithTrigger.getTriggerContext()
            )).orElse(null);
        } catch (Exception e) {
            logError(flowWithTrigger, e);
            return null;
        }
    }

    private void logError(FlowWithPollingTrigger flowWithPollingTriggerNextDate, Throwable e) {
        Logger logger = flowWithPollingTriggerNextDate.getConditionContext().getRunContext().logger();

        logger.warn(
            "[namespace: {}] [flow: {}] [trigger: {}] [date: {}] Evaluate Failed with error '{}'",
            flowWithPollingTriggerNextDate.getFlow().getNamespace(),
            flowWithPollingTriggerNextDate.getFlow().getId(),
            flowWithPollingTriggerNextDate.getTriggerContext().getTriggerId(),
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

        logger.error(
            "[namespace: {}] [flow: {}] [trigger: {}] [date: {}] Evaluate Failed with error '{}'",
            flow.getNamespace(),
            flow.getId(),
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
            log.debug(
                "[namespace: {}] [flow: {}] [trigger: {}] [date: {}] Scheduling evaluation to the worker",
                flowWithTrigger.getFlow().getNamespace(),
                flowWithTrigger.getFlow().getId(),
                flowWithTrigger.getTriggerContext().getDate(),
                flowWithTrigger.getTriggerContext().getTriggerId()
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

        public static FlowWithPollingTriggerNextDate of(FlowWithPollingTrigger f) {
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
