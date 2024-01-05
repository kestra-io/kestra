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
import io.kestra.core.models.triggers.TriggerContext;
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
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwSupplier;

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
    private final Map<String, ZonedDateTime> lastEvaluate = new ConcurrentHashMap<>();

    // The triggerStateSavedLock must be used when accessing triggerStateSaved
    protected final Object triggerStateSavedLock = new Object();
    private final Map<String, Trigger> triggerStateSaved = new ConcurrentHashMap<>();
    protected SchedulerTriggerStateInterface triggerState;

    // schedulable and schedulableNextDate must be volatile and their access synchronized as they are updated and read by different threads.
    @Getter
    private volatile List<FlowWithTrigger> schedulable = new ArrayList<>();
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
        flowListeners.run();

        ScheduledFuture<?> handle = scheduleExecutor.scheduleAtFixedRate(
            this::handle,
            0,
            1,
            TimeUnit.SECONDS
        );

        flowListeners.listen(this::computeSchedulable);

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

        // remove trigger on flow update
        this.flowListeners.listen((flow, previous) -> {
            synchronized (triggerStateSavedLock) {
                if (flow.isDeleted()) {
                    ListUtils.emptyOnNull(flow.getTriggers())
                        .forEach(abstractTrigger -> {
                            Trigger trigger = Trigger.of(flow, abstractTrigger);

                            triggerStateSaved.remove(trigger.uid());
                            triggerQueue.delete(trigger);
                        });
                } else if (previous != null) {
                    FlowService
                        .findRemovedTrigger(flow, previous)
                        .forEach(abstractTrigger -> {
                            Trigger trigger = Trigger.of(flow, abstractTrigger);

                            triggerStateSaved.remove(trigger.uid());
                            triggerQueue.delete(trigger);
                        });
                }
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
                    var triggerExecution = new SchedulerExecutionWithTrigger(
                        workerTriggerResult.getExecution().get(),
                        workerTriggerResult.getTriggerContext()
                    );
                    this.handleEvaluatePollingTriggerResult(triggerExecution);
                }
                else {
                    // previously, if no interval the trigger was executed immediately. I think only the Schedule trigger has no interval
                    // now that all triggers are sent to the worker, we need to do this to avoid issues with backfills
                    // as the same trigger will be evaluated multiple-time which is not supported by the 'addToRunning' method
                    // if the trigger didn't trigger an execution, we will need to clear the evaluation lock
                    // TODO now that Schedule is executed by the Scheduler this could be relaxed
                    if (workerTriggerResult.getTrigger() instanceof PollingTriggerInterface &&
                        ((PollingTriggerInterface) workerTriggerResult.getTrigger()).getInterval() != null) {
                        var triggerNotRunning = Trigger.of(workerTriggerResult.getTriggerContext());
                        triggerState.save(triggerNotRunning);
                    }
                }
            }
        );
    }

    // must be synchronized as it update schedulableNextDate and schedulable, and will be executed on the flow listener thread
    private synchronized void computeSchedulable(List<Flow> flows) {
        this.schedulableNextDate = new HashMap<>();

        this.schedulable = flows
            .stream()
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .filter(flow -> !flow.isDisabled() && !(flow instanceof FlowWithException))
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled() && abstractTrigger instanceof PollingTriggerInterface)
                .map(trigger -> {
                    RunContext runContext = runContextFactory.of(flow, trigger);

                    return new FlowWithTrigger(
                        flow,
                        trigger,
                        runContext,
                        conditionService.conditionContext(runContext, flow, null)
                    );
                })
            )
            .toList();
    }

    private void handle() {
        if (!this.isReady) {
            log.warn("Scheduler is not ready, waiting");
        }

        metricRegistry
            .counter(MetricRegistry.SCHEDULER_LOOP_COUNT)
            .increment();

        ZonedDateTime now = now();

        if (log.isTraceEnabled()) {
            log.trace(
                "Scheduler next iteration for {} with {} schedulables of {} flows",
                now,
                schedulable.size(),
                this.flowListeners.flows().size()
            );
        }

        synchronized (this) {
            // get all triggers that are ready from evaluation
            List<FlowWithPollingTriggerNextDate> readyForEvaluate = schedulable
                .stream()
                .filter(f -> conditionService.isValid(f.getFlow(), f.getTrigger(), f.getConditionContext()))
                .map(flowWithTrigger -> FlowWithPollingTrigger.builder()
                    .flow(flowWithTrigger.getFlow())
                    .trigger(flowWithTrigger.getTrigger())
                    .pollingTrigger((PollingTriggerInterface) flowWithTrigger.getTrigger())
                    .conditionContext(flowWithTrigger.getConditionContext())
                    .triggerContext(TriggerContext
                        .builder()
                        .tenantId(flowWithTrigger.getFlow().getTenantId())
                        .namespace(flowWithTrigger.getFlow().getNamespace())
                        .flowId(flowWithTrigger.getFlow().getId())
                        .flowRevision(flowWithTrigger.getFlow().getRevision())
                        .triggerId(flowWithTrigger.getTrigger().getId())
                        .date(now())
                        .build()
                    )
                    .build()
                )
                .filter(f -> this.isEvaluationInterval(f, now))
                .filter(f -> !this.isTriggerRunning(f))
                .filter(f -> this.isExecutionNotRunning(f, now))
                .map(f -> {
                    try {
                        Trigger lastTrigger = this.getLastTrigger(f, now);

                        return FlowWithPollingTriggerNextDate.of(
                            f,
                            f.getPollingTrigger().nextEvaluationDate(f.getConditionContext(), Optional.of(lastTrigger))
                        );
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
                        // If and interval the trigger is executed by the Worker.
                        // Normally, only the Schedule trigger has no interval.
                        var triggerRunning = Trigger.of(f.getTriggerContext(), now);
                        triggerState.save(triggerRunning);

                        try {
                            this.sendPollingTriggerToWorker(f);
                        } catch (InternalException e) {
                            logger.error(
                                "[namespace: {}] [flow: {}] [trigger: {}] Unable to send polling trigger to worker",
                                f.getFlow().getNamespace(),
                                f.getFlow().getId(),
                                f.getTrigger().getId(),
                                e
                            );
                        }
                    } else if(f.getPollingTrigger() instanceof Schedule) {
                        // This is the Schedule, all others trigger should have an interval.
                        // So we evaluate it now as there is no need to send it to the worker.
                        // Schedule didn't use the triggerState to allow backfill.
                        try {
                            var schedulerExecutionWithTrigger = evaluateScheduleTrigger(f);
                            this.handleEvaluatePollingTriggerResult(schedulerExecutionWithTrigger);
                        } catch (Exception e) {
                            logger.error(
                                "[namespace: {}] [flow: {}] [trigger: {}] Evaluate schedule trigger failed",
                                f.getFlow().getNamespace(),
                                f.getFlow().getId(),
                                f.getTrigger().getId(),
                                e
                            );
                        }
                    } else {
                        logger.error(
                            "[namespace: {}] [flow: {}] [trigger: {}] Polling trigger must have an interval (except the Schedule)",
                            f.getFlow().getNamespace(),
                            f.getFlow().getId(),
                            f.getTrigger().getId()
                        );
                    }
                });
        }
    }

    private void handleEvaluatePollingTriggerResult(SchedulerExecutionWithTrigger result) {
        Stream.of(result)
            .filter(Objects::nonNull)
            .peek(this::log)
            .forEach(this::saveLastTriggerAndEmitExecution);
    }

    private boolean isExecutionNotRunning(FlowWithPollingTrigger f, ZonedDateTime now) {
        Trigger lastTrigger = null;
        try {
            lastTrigger = this.getLastTrigger(f, now);
        } catch (Exception e) {
            logError(f, e);
            return false;
        }

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

    private Trigger getLastTrigger(FlowWithPollingTrigger f, ZonedDateTime now) throws Exception {
        return triggerState
            .findLast(f.getTriggerContext())
            .orElseGet(throwSupplier(() -> {
                ZonedDateTime nextDate = f.getPollingTrigger().nextEvaluationDate(f.getConditionContext(), Optional.empty());

                Trigger build = Trigger.builder()
                    .tenantId(f.getTriggerContext().getTenantId())
                    .date(nextDate.compareTo(now) < 0 ? nextDate : now)
                    .flowId(f.getFlow().getId())
                    .flowRevision(f.getFlow().getRevision())
                    .namespace(f.getFlow().getNamespace())
                    .triggerId(f.getTriggerContext().getTriggerId())
                    .updatedDate(Instant.now())
                    .build();

                // We don't find a trigger, so the flow was never started.
                // We create a trigger context with previous date in the past.
                // This fix an edge case when the evaluation loop of the scheduler didn't catch up so new triggers was detected but not stored.
                synchronized (triggerStateSavedLock) {
                    if (triggerStateSaved.containsKey(build.uid())) {
                        Trigger cachedTrigger = triggerStateSaved.get(build.uid());

                        triggerState.save(build);
                        triggerStateSaved.remove(build.uid());

                        return cachedTrigger;
                    } else {
                        triggerStateSaved.put(build.uid(), build);
                    }
                }

                return build;
            }));
    }

    private boolean isEvaluationInterval(FlowWithPollingTrigger flowWithPollingTrigger, ZonedDateTime now) {
        if (flowWithPollingTrigger.getPollingTrigger().getInterval() == null) {
            return true;
        }

        String key = flowWithPollingTrigger.getTriggerContext().uid();

        if (!this.lastEvaluate.containsKey(key)) {
            this.lastEvaluate.put(key, now);
            return true;
        }

        boolean result = this.lastEvaluate.get(key)
            .plus(flowWithPollingTrigger.getPollingTrigger().getInterval())
            .compareTo(now) < 0;

        if (result) {
            this.lastEvaluate.put(key, now);
        }

        return result;
    }

    private boolean isTriggerRunning(FlowWithPollingTrigger flowWithPollingTrigger) {
        // We don't want to check if a trigger is running for Schedule trigger which didn't use the triggerState store
        if (flowWithPollingTrigger.getPollingTrigger().getInterval() == null) {
            return false;
        }

        var runningTrigger = this.triggerState
            .findLast(flowWithPollingTrigger.getTriggerContext())
            .filter(trigger -> trigger.getEvaluateRunningDate() != null);
        return runningTrigger.isPresent();
    }

    protected void saveLastTriggerAndEmitExecution(SchedulerExecutionWithTrigger executionWithTrigger) {
        Trigger trigger = Trigger.of(
            executionWithTrigger.getTriggerContext(),
            executionWithTrigger.getExecution()
        );

        synchronized (triggerStateSavedLock) {
            this.triggerState.save(trigger);
            // we need to be sure that the tenantId is propagated from the trigger to the execution
            var execution = executionWithTrigger.getExecution().withTenantId(executionWithTrigger.getTriggerContext().getTenantId());
            this.executionQueue.emit(execution);
        }
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
                flowWithTrigger.getTrigger()
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
                        flowWithPollingTrigger.getTrigger().getId(),
                        flowWithPollingTrigger.getTrigger().getType(),
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
            .trigger(flowWithTriggerWithDefault.trigger)
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
        private AbstractTrigger trigger;
        private PollingTriggerInterface pollingTrigger;
        private TriggerContext triggerContext;
        private ConditionContext conditionContext;

        public FlowWithPollingTrigger from(Flow flow) throws InternalException {
            AbstractTrigger abstractTrigger = flow.getTriggers()
                .stream()
                .filter(a -> a.getId().equals(this.trigger.getId()) && a instanceof PollingTriggerInterface)
                .findFirst()
                .orElseThrow(() -> new InternalException("Couldn't find the trigger '" + this.trigger.getId() + "' on flow '" + flow.uid() + "'"));

            return this.toBuilder()
                .flow(flow)
                .trigger(abstractTrigger)
                .pollingTrigger((PollingTriggerInterface) abstractTrigger)
                .build();
        }
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class FlowWithPollingTriggerNextDate extends FlowWithPollingTrigger {
        private ZonedDateTime next;

        public static FlowWithPollingTriggerNextDate of(FlowWithPollingTrigger f, ZonedDateTime next) {
            return FlowWithPollingTriggerNextDate.builder()
                .flow(f.getFlow())
                .trigger(f.getTrigger())
                .pollingTrigger(f.getPollingTrigger())
                .conditionContext(f.getConditionContext())
                .triggerContext(TriggerContext.builder()
                    .tenantId(f.getTriggerContext().getTenantId())
                    .namespace(f.getTriggerContext().getNamespace())
                    .flowId(f.getTriggerContext().getFlowId())
                    .flowRevision(f.getTriggerContext().getFlowRevision())
                    .triggerId(f.getTriggerContext().getTriggerId())
                    .date(next)
                    .build()
                )
                .next(next)
                .build();
        }
    }

    @AllArgsConstructor
    @Getter
    public static class FlowWithTrigger {
        private final Flow flow;
        private final AbstractTrigger trigger;
        private final RunContext runContext;
        private final ConditionContext conditionContext;
    }
}
