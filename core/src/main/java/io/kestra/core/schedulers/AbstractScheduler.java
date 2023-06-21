package io.kestra.core.schedulers;

import com.google.common.base.Throwables;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Slf4j
@Singleton
public abstract class AbstractScheduler implements Scheduler {
    protected final ApplicationContext applicationContext;
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final QueueInterface<WorkerTrigger> workerTriggerQueue;
    private final QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;
    protected final FlowListenersInterface flowListeners;
    private final RunContextFactory runContextFactory;
    private final MetricRegistry metricRegistry;
    private final ConditionService conditionService;
    private final TaskDefaultService taskDefaultService;
    protected SchedulerExecutionStateInterface executionState;
    protected Boolean isReady = false;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ZonedDateTime> lastEvaluate = new ConcurrentHashMap<>();

    // The triggerStateSavedLock must be used when accessing triggerStateSaved
    private final Object triggerStateSavedLock = new Object();
    private final Map<String, Trigger> triggerStateSaved = new ConcurrentHashMap<>();
    protected SchedulerTriggerStateInterface triggerState;

    // schedulable and schedulableNextDate must be volatile and their access synchronized as they are updated and read by different threads.
    @Getter
    private volatile List<FlowWithTrigger> schedulable = new ArrayList<>();
    @Getter
    private volatile Map<String, FlowWithPollingTriggerNextDate> schedulableNextDate = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Inject
    public AbstractScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners
    ) {
        this.applicationContext = applicationContext;
        this.executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.workerTriggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGER_NAMED));
        this.workerTriggerResultQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED));
        this.flowListeners = flowListeners;
        this.runContextFactory = applicationContext.getBean(RunContextFactory.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.taskDefaultService = applicationContext.getBean(TaskDefaultService.class);
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
                    log.error("Executor fatal exception", e);
                    applicationContext.close();
                    Runtime.getRuntime().exit(1);
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
            Scheduler.class,
            workerTriggerResult -> {
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
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .filter(flow -> !flow.isDisabled())
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled())
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
            .filter(flowWithTrigger -> flowWithTrigger.getTrigger() instanceof PollingTriggerInterface)
            .collect(Collectors.toList());
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
                    Trigger lastTrigger = this.getLastTrigger(f, now);

                    return FlowWithPollingTriggerNextDate.of(
                        f,
                        f.getPollingTrigger().nextEvaluationDate(f.getConditionContext(), Optional.of(lastTrigger))
                    );
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
        Trigger lastTrigger = this.getLastTrigger(f, now);

        if (lastTrigger.getExecutionId() == null) {
            return true;
        }

        Optional<Execution> execution = executionState.findById(lastTrigger.getExecutionId());

        // executionState hasn't received the execution, we skip
        if (execution.isEmpty()) {
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

        // TODO if we set the state in the trigger after it has been started we can avoid getting the execution and
        // check that if an executionId but no state, this means the execution is not started
        // we need to have {@code lastTrigger.getExecutionId() == null} to be tell the execution is not running.
        // the scheduler will clean the execution from the trigger and we don't keep only terminated state as an end.
        if (log.isDebugEnabled()) {
            log.debug(
                "[namespace: {}] [flow: {}] [trigger: {}] Execution '{}' is still '{}', updated at '{}'",
                lastTrigger.getNamespace(),
                lastTrigger.getFlowId(),
                lastTrigger.getTriggerId(),
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

        if (executionWithTrigger.getExecution().getTrigger() != null) {
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
            executionWithTrigger.getTriggerContext().getDate(),
            executionWithTrigger.getExecution().getId(),
            now
        );
    }

    private Trigger getLastTrigger(FlowWithPollingTrigger f, ZonedDateTime now) {
        return triggerState
            .findLast(f.getTriggerContext())
            .orElseGet(() -> {
                ZonedDateTime nextDate = f.getPollingTrigger().nextEvaluationDate(f.getConditionContext(), Optional.empty());

                Trigger build = Trigger.builder()
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
            });
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
            this.executionQueue.emit(executionWithTrigger.getExecution());
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

        this.workerTriggerQueue.emit(WorkerTrigger
            .builder()
            .trigger(flowWithTriggerWithDefault.trigger)
            .triggerContext(flowWithTriggerWithDefault.triggerContext)
            .conditionContext(flowWithTriggerWithDefault.conditionContext)
            .build()
        );
    }

    @Override
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
                .filter(a -> a.getId().equals(this.trigger.getId()))
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
