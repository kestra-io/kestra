package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.event.Level;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.*;
import io.kestra.core.runners.Executor;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.utils.*;
import io.kestra.executor.configuration.ExecutorConfiguration;
import io.kestra.executor.handler.*;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.trigger.Webhook;

import io.micrometer.core.instrument.Timer;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.*;

@Singleton
@Slf4j
public class DefaultExecutor extends AbstractService implements Executor {
    private static final String UNABLE_TO_DESERIALIZE_AN_EXECUTION = "Unable to deserialize an execution: {}";

    private final DispatchQueueInterface<Execution> executionQueue;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final DispatchQueueInterface<ExecutionEvent> executionEventQueue;
    private final BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue;
    private final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final BroadcastQueueInterface<ExecutionKilled> killQueue;
    private final DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    private final DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;
    private final DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue;
    private final DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue;
    private final DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue;

    private final ExecutorService executorService;
    private final ExecutionService executionService;
    private final FlowTriggerService flowTriggerService;
    private final SLAService slaService;
    private final MaintenanceService maintenanceService;
    private final FlowMetaStoreInterface flowMetaStore;

    private final ExecutionStateStore executionStateStore;
    private final ExecutionDelayStateStore executionDelayStateStore;
    private final SLAMonitorStateStore slaMonitorStateStore;
    private final ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor;
    private final TriggerEventQueue triggerEventQueue;

    private final MetricRegistry metricRegistry;

    // The context captured at construction time.
    // The static context returned by KestraContext.getContext() might change if the context is restarted inside the same JVM
    // which can occur at least in tests.
    private final KestraContext kestraContext;

    private final RunContextFactory runContextFactory;

    private final ExecutionCommandMessageHandler executionCommandMessageHandler;
    private final ExecutionEventMessageHandler executionEventMessageHandler;
    private final WorkerTaskResultMessageHandler workerTaskResultMessageHandler;
    private final ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;
    private final SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;
    private final SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;
    private final MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;
    private final LoopExecutionEventMessageHandler loopExecutionEventMessageHandler;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> executionDelayFuture;
    private ScheduledFuture<?> monitorSLAFuture;

    // Thread-safe: populated by run() but iterated from maintenance listener and shutdown threads.
    private final List<Runnable> receiveCancellations = new CopyOnWriteArrayList<>();
    private final List<QueueSubscriber<?>> queueSubscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final java.util.concurrent.ExecutorService workerTaskResultExecutorService;
    private final java.util.concurrent.ExecutorService executionExecutorService;
    private final int numberOfThreads;

    private Timer flowTriggerProcessingTimer;
    private Timer slaMonitorLoopTimer;
    private Timer executionDelayLoopTimer;

    @Inject
    public DefaultExecutor(
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        ExecutorsUtils executorsUtils,
        ExecutorConfiguration executorConfiguration,
        KestraContext kestraContext,
        DispatchQueueInterface<Execution> executionQueue,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        DispatchQueueInterface<ExecutionEvent> executionEventQueue,
        BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue,
        DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue,
        BroadcastQueueInterface<ExecutionKilled> killQueue,
        DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue,
        DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue,
        DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue,
        DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue,
        DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue,
        ExecutorService executorService,
        ExecutionService executionService,
        FlowTriggerService flowTriggerService,
        SLAService slaService,
        MaintenanceService maintenanceService,
        FlowMetaStoreInterface flowMetaStore,
        ExecutionStateStore executionStateStore,
        ExecutionDelayStateStore executionDelayStateStore,
        SLAMonitorStateStore slaMonitorStateStore,
        ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor,
        TriggerEventQueue triggerEventQueue,
        MetricRegistry metricRegistry,
        RunContextFactory runContextFactory,
        ExecutionCommandMessageHandler executionCommandMessageHandler,
        ExecutionEventMessageHandler executionEventMessageHandler,
        WorkerTaskResultMessageHandler workerTaskResultMessageHandler,
        ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler,
        SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler,
        SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler,
        MultipleConditionEventMessageHandler multipleConditionEventMessageHandler,
        LoopExecutionEventMessageHandler loopExecutionEventMessageHandler) {
        super(ServiceType.EXECUTOR, eventPublisher);

        this.kestraContext = kestraContext;
        this.executionQueue = executionQueue;
        this.executionCommandQueue = executionCommandQueue;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
        this.executionEventQueue = executionEventQueue;
        this.followExecutionEventQueue = followExecutionEventQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.killQueue = killQueue;
        this.subflowExecutionResultQueue = subflowExecutionResultQueue;
        this.subflowExecutionEndQueue = subflowExecutionEndQueue;
        this.multipleConditionEventQueue = multipleConditionEventQueue;
        this.loopExecutionEventQueue = loopExecutionEventQueue;
        this.executionStatisticQueue = executionStatisticQueue;
        this.executorService = executorService;
        this.executionService = executionService;
        this.flowTriggerService = flowTriggerService;
        this.slaService = slaService;
        this.maintenanceService = maintenanceService;
        this.flowMetaStore = flowMetaStore;
        this.executionStateStore = executionStateStore;
        this.executionDelayStateStore = executionDelayStateStore;
        this.slaMonitorStateStore = slaMonitorStateStore;
        this.concurrencySlotReleaseProcessor = concurrencySlotReleaseProcessor;
        this.triggerEventQueue = triggerEventQueue;
        this.metricRegistry = metricRegistry;
        this.runContextFactory = runContextFactory;
        this.executionCommandMessageHandler = executionCommandMessageHandler;
        this.executionEventMessageHandler = executionEventMessageHandler;
        this.workerTaskResultMessageHandler = workerTaskResultMessageHandler;
        this.executionKilledExecutionMessageHandler = executionKilledExecutionMessageHandler;
        this.subflowExecutionResultMessageHandler = subflowExecutionResultMessageHandler;
        this.subflowExecutionEndMessageHandler = subflowExecutionEndMessageHandler;
        this.multipleConditionEventMessageHandler = multipleConditionEventMessageHandler;
        this.loopExecutionEventMessageHandler = loopExecutionEventMessageHandler;

        // By default, we start available processors count threads with a minimum of 4 by executor service
        // for the worker task result queue and the execution queue.
        // Other queues would not benefit from more consumers.
        int threadCount = executorConfiguration.threadCount() != null ? executorConfiguration.threadCount() : 0;
        this.numberOfThreads = threadCount != 0 ? threadCount : Math.max(4, kestraContext.getAllocatedCpuCores());
        this.workerTaskResultExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-worker-task-result-executor");
        this.executionExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-execution-event-executor");

        setState(ServiceState.CREATED);
    }

    @PostConstruct
    void initMetrics() {
        // create metrics to store thread count
        this.metricRegistry.gauge(MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT, MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT_DESCRIPTION, numberOfThreads);

        // init internal timers
        this.flowTriggerProcessingTimer = this.metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION, MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION_DESCRIPTION);
        this.slaMonitorLoopTimer = this.metricRegistry.timer(MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION_DESCRIPTION);
        this.executionDelayLoopTimer = this.metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION_DESCRIPTION);
    }

    @Override
    public Set<Metric> getMetrics() {
        if (this.metricRegistry == null) {
            // can arrive if called before the instance is fully created
            return Collections.emptySet();
        }

        Stream<String> metrics = Stream.of(
            MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT
        );

        return metrics
            .flatMap(metric -> Optional.ofNullable(metricRegistry.findGauge(metric)).stream())
            .map(Metric::of)
            .collect(Collectors.toSet());
    }

    @Override
    public void run() {
        guardedStart(this::doRun, () ->
        {
            if (this.maintenanceService.isInMaintenanceMode()) {
                enterMaintenance();
            } else {
                setState(ServiceState.RUNNING);
            }
            log.info("Executor started with {} thread(s)", numberOfThreads);
        });
    }

    private void doRun() {
        // listen to executor related queues
        this.queueSubscribers.addFirst(this.executionQueue.subscriber().subscribe(this::executionQueue));
        this.queueSubscribers.addFirst(
            this.executionEventQueue.subscriber().subscribeBatch(
                executions ->
                {
                    // process execution message grouped by executionId to avoid concurrency as the execution level as it would
                    List<CompletableFuture<Void>> perExecutionFutures = executions.stream()
                        .filter(Either::isLeft)
                        .collect(Collectors.groupingBy(either -> either.getLeft().executionId()))
                        .values()
                        .stream()
                        .map(eithers -> CompletableFuture.runAsync(() ->
                        {
                            eithers.forEach(this::executionEventQueue);
                        }, executionExecutorService))
                        .toList();

                    // directly process deserialization issues as most of the time there will be none
                    executions.stream()
                        .filter(Either::isRight)
                        .forEach(either -> executionEventQueue(either));

                    CompletableFuture.allOf(perExecutionFutures.toArray(CompletableFuture[]::new)).join();
                }
            )
        );
        this.queueSubscribers.addFirst(this.workerTaskResultQueue.subscriber().subscribeBatch(workerTaskResults ->
        {
            // process worker task results grouped by executionId, to avoid concurrency at the execution level:
            // joining a later sibling's result (e.g. a failing task) before an earlier one can terminate a flowable,
            // and silently drop the earlier task's outputs, which are never joined afterward.
            List<CompletableFuture<Void>> perExecutionFutures = workerTaskResults.stream()
                .filter(Either::isLeft)
                .collect(Collectors.groupingBy(either -> either.getLeft().getTaskRun().getExecutionId()))
                .values()
                .stream()
                .map(eithers -> CompletableFuture.runAsync(() ->
                {
                    eithers.forEach(this::workerTaskResultQueue);
                }, workerTaskResultExecutorService))
                .toList();

            // directly process deserialization issues as most of the time there will be none
            workerTaskResults.stream()
                .filter(Either::isRight)
                .forEach(either -> workerTaskResultQueue(either));

            CompletableFuture.allOf(perExecutionFutures.toArray(CompletableFuture[]::new)).join();
        }
        ));
        this.queueSubscribers.addFirst(this.executionCommandQueue.subscriber().subscribe(this::executionCommandQueue));
        this.queueSubscribers.addFirst(this.subflowExecutionResultQueue.subscriber().subscribe(this::subflowExecutionResultQueue));
        this.queueSubscribers.addFirst(this.subflowExecutionEndQueue.subscriber().subscribe(this::subflowExecutionEndQueue));
        this.queueSubscribers.addFirst(this.multipleConditionEventQueue.subscriber().subscribe(this::multipleConditionEventQueue));
        this.queueSubscribers.addFirst(this.loopExecutionEventQueue.subscriber().subscribe(this::loopExecutionEventQueue));
        this.queueSubscribers.addFirst(this.killQueue.subscriber().subscribe(this::killQueue));

        // Register maintenance listener
        this.receiveCancellations.add(this.maintenanceService.listen(new MaintenanceService.MaintenanceListener() {
            @Override
            public void onMaintenanceModeEnter() {
                DefaultExecutor.this.enterMaintenance();
            }

            @Override
            public void onMaintenanceModeExit() {
                DefaultExecutor.this.exitMaintenance();
            }
        })::dispose);

        // A stop may have timed out waiting for this startup and already closed the scheduled
        // pool — don't schedule the loops or start their watchers on it.
        if (isStopRequested()) {
            return;
        }

        // Start delay and monitoring loops
        executionDelayFuture = scheduledExecutorService.scheduleAtFixedRate(
            this::executionDelayLoop,
            0,
            1,
            TimeUnit.SECONDS
        );
        monitorSLAFuture = scheduledExecutorService.scheduleAtFixedRate(
            this::executionSLAMonitorLoop,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exceptions on the scheduledDelay thread
        Thread.ofVirtual().name("executor-delay-exception-watcher").start(
            () ->
            {
                Await.until(executionDelayFuture::isDone);

                try {
                    executionDelayFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    // An exception during shutdown is teardown noise (e.g. closed datasource), not a reason to escalate.
                    // We avoid closing the Executor if the exception is a CannotCreateTransactionException as it may be transient
                    if (!isStopRequested() && e.getCause() != null && !e.getCause().getClass().getSimpleName().equals("CannotCreateTransactionException")) {
                        log.error("Executor fatal exception in the scheduledDelay thread", e);
                        close();
                        kestraContext.shutdown();
                    }
                }
            }
        );

        // look at exceptions on the scheduledSLAMonitorFuture thread
        Thread.ofVirtual().name("executor-sla-monitor-exception-watcher").start(
            () ->
            {
                Await.until(monitorSLAFuture::isDone);

                try {
                    monitorSLAFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    // An exception during shutdown is teardown noise (e.g. closed datasource), not a reason to escalate.
                    // We avoid closing the Executor if the exception is a CannotCreateTransactionException as it may be transient
                    if (!isStopRequested() && e.getCause() != null && !e.getCause().getClass().getSimpleName().equals("CannotCreateTransactionException")) {
                        log.error("Executor fatal exception in the scheduledSLAMonitor thread", e);
                        close();
                        kestraContext.shutdown();
                    }
                }
            }
        );

    }

    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }
        Execution execution = either.getLeft();
        // Always persist first so the execution is present in the DB even if kill-switched.
        try {
            executionStateStore.create(execution);
        } catch (Exception e) {
            log.error("Unable to create execution {}", execution.getId(), e);
        }
        EvaluationType evaluationType = killSwitchService.evaluate(execution);
        if (evaluationType.isKillSwitched(execution)) {
            killSwitchActionService.handle(evaluationType, execution.getTenantId(), execution.getId());
            return;
        }
        var eventType = execution.getState().isCreated() ? ExecutionEventType.CREATED : ExecutionEventType.UPDATED;
        executionEventMessageHandler.handle(new ExecutionEvent(execution, eventType)).ifPresent(this::toExecution);
    }

    private void executionCommandQueue(Either<ExecutionCommand, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        executionCommandMessageHandler.handle(either.getLeft()).ifPresent(this::toExecution);
    }

    private void executionEventQueue(Either<ExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }
        executionEventMessageHandler.handle(either.getLeft()).ifPresent(this::toExecution);
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage(), either.getRight());
            return;
        }
        workerTaskResultMessageHandler.handle(either.getLeft()).ifPresent(this::toExecution);
    }

    private void killQueue(Either<ExecutionKilled, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a killed execution: {}", either.getRight().getMessage());
            return;
        }

        final ExecutionKilled event = either.getLeft();

        // Check whether the event should be handled by the executor.
        if (event.getState() == ExecutionKilled.State.EXECUTED) {
            // Event was already handled by the Executor. Ignore it.
            return;
        }

        if (!(event instanceof ExecutionKilledExecution killedExecution)) {
            return;
        }

        // Transmit the new execution state. Note that the execution
        // will eventually transition to KILLED state before sub-flow executions are actually killed.
        // This behavior is acceptable due to the fire-and-forget nature of the killing event.
        executionKilledExecutionMessageHandler.handle(killedExecution).ifPresent(executor -> this.toExecution(executor, true));
    }

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution result: {}", either.getRight().getMessage());
            return;
        }
        subflowExecutionResultMessageHandler.handle(either.getLeft()).ifPresent(this::toExecution);
    }

    private void subflowExecutionEndQueue(Either<SubflowExecutionEnd, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution end: {}", either.getRight().getMessage());
            return;
        }
        subflowExecutionEndMessageHandler.handle(either.getLeft());
    }

    private void multipleConditionEventQueue(Either<MultipleConditionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a multiple condition event: {}", either.getRight().getMessage());
            return;
        }
        multipleConditionEventMessageHandler.handle(either.getLeft());
    }

    private void loopExecutionEventQueue(Either<LoopExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a loop execution event: {}", either.getRight().getMessage());
            return;
        }
        loopExecutionEventMessageHandler.handle(either.getLeft()).ifPresent(this::toExecution);
    }

    /**
     * ExecutionDelay is currently two types of execution:
     * <br/>
     * - Paused flow that will be restarted after an interval/timeout
     * <br/>
     * - Failed flow that will be retried after an interval
     **/
    private void executionDelayLoop() {
        if (isStopRequested() || this.isPaused.get()) {
            return;
        }

        executionDelayLoopTimer.record(() ->
        {
            // Collect the resulting executors during the transaction and emit them only AFTER
            // processExpired() commits. Emitting inside the transaction races the queue consumer:
            // on a non-transactional queue (Kafka) a new execution created by replay
            // (CREATE_NEW_EXECUTION / RESTART_FAILED_FLOW) can be consumed before its INSERT is
            // visible, so the executor's lock finds no row, silently skips it ("not ready for now"),
            // and the new execution is dropped — the retry chain never runs.
            List<ExecutorContext> toEmit = new ArrayList<>();
            executionDelayStateStore.processExpired(Instant.now(), executionDelay ->
            {
                Optional<ExecutorContext> maybeExecutor = executionStateStore.lock(executionDelay.getExecutionId(), execution ->
                {
                    ExecutorContext executor = new ExecutorContext(execution);

                    metricRegistry
                        .counter(
                            MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT_DESCRIPTION,
                            metricRegistry.tags(executor.getExecution())
                        )
                        .increment();

                    try {
                        // Handle paused tasks and scheduledAt
                        // Also skip if the execution is being killed (KILLING is not yet terminated but must not be resumed).
                        if (
                            executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW)
                                && !execution.getState().isTerminated()
                                && execution.getState().getCurrent() != State.Type.KILLING
                        ) {
                            if (executionDelay.getTaskRunId() == null) {
                                // if taskRunId is null, this means we restart a flow that was delayed at startup (scheduled on)
                                Execution markAsExecution = execution.withState(executionDelay.getState());
                                executor = executor.withExecution(markAsExecution, "pausedRestart");
                            } else {
                                // if there is a taskRun it means we restart a paused task
                                FlowInterface flow = flowMetaStore.findByExecution(execution).orElseThrow();
                                Execution markAsExecution = executionService.markAs(
                                    execution,
                                    flow,
                                    executionDelay.getTaskRunId(),
                                    executionDelay.getState()
                                );

                                executor = executor.withExecution(markAsExecution, "pausedRestart");
                            }
                        }
                        // Handle failed task retries — skip if the execution is being killed so the retry does not race the kill
                        else if (
                            executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)
                                && execution.getState().getCurrent() != State.Type.KILLING
                        ) {
                            FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                            Execution newAttempt = executionService.retryTask(
                                execution,
                                flow,
                                executionDelay.getTaskRunId()
                            );
                            executor = executor.withExecution(newAttempt, "retryFailedTask");
                        }
                        // Handle failed flow retries — skip if the execution is being killed so the retry does not race the kill
                        else if (
                            executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)
                                && execution.getState().getCurrent() != State.Type.KILLING
                        ) {
                            FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                            Execution newExecution = executionService.replay(executor.getExecution(), flow, null, null, Optional.empty());
                            executor = executor.withExecution(newExecution, "retryFailedFlow");
                        }
                        // Handle WaitFor
                        else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)) {
                            Execution newExecution = executionService.retryWaitFor(executor.getExecution(), executionDelay.getTaskRunId());
                            executor = executor.withExecution(newExecution, "continueLoop");
                        }
                    } catch (Exception e) {
                        executor = executorService.handleFailedExecutionFromExecutor(executor, e);
                    }

                    return executor;
                });

                maybeExecutor.ifPresent(toEmit::add);
            });

            // Transaction has committed here: the new/updated executions are now durably visible,
            // so emitting their events cannot be consumed before the state store can see them.
            toEmit.forEach(this::toExecution);
        });
    }

    private void executionSLAMonitorLoop() {
        if (isStopRequested() || this.isPaused.get()) {
            return;
        }

        slaMonitorLoopTimer.record(() ->
        {
            slaMonitorStateStore.processExpired(Instant.now(), slaMonitor ->
            {
                Optional<ExecutorContext> maybeExecutor = executionStateStore.lock(slaMonitor.getExecutionId(), execution ->
                {
                    FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                    Optional<SLA> sla = flow.getSla().stream().filter(s -> s.getId().equals(slaMonitor.getSlaId())).findFirst();
                    if (sla.isEmpty()) {
                        // this can happen in case the flow has been updated and the SLA removed
                        log.debug("Cannot find the SLA '{}' in the flow for execution '{}', ignoring it.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                        return null;
                    }

                    // There can be a race: a monitor can be found, but the execution terminated.
                    // This particularly could occur in ElasticSearch due to refresh.
                    if (executionService.isTerminated(flow, execution)) {
                        return null;
                    }

                    metricRegistry
                        .counter(MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT_DESCRIPTION, metricRegistry.tags(execution))
                        .increment();

                    ExecutorContext executor = new ExecutorContext(execution, flow);
                    try {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        Optional<Violation> violation = slaService.evaluateExecutionMonitoringSLA(runContext, executor.getExecution(), sla.get());
                        if (violation.isPresent()) { // should always be true
                            log.info("Processing expired SLA monitor '{}' for execution '{}'.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                            executor = executorService.processViolation(runContext, executor, violation.get());

                            metricRegistry
                                .counter(
                                    MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION,
                                    metricRegistry.tags(executor.getExecution())
                                )
                                .increment();
                        }
                    } catch (Exception e) {
                        executor = executorService.handleFailedExecutionFromExecutor(executor, e);
                    }

                    return executor;
                });

                maybeExecutor.ifPresent(this::toExecution);
            });
        });
    }

    private void enterMaintenance() {
        this.queueSubscribers.forEach(QueueSubscriber::pause);

        this.isPaused.set(true);
        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.queueSubscribers.forEach(QueueSubscriber::resume);

        this.isPaused.set(false);
        this.setState(ServiceState.RUNNING);
    }

    private void toExecution(ExecutorContext executor) {
        toExecution(executor, false);
    }

    private void toExecution(ExecutorContext executor, boolean ignoreFailure) {
        try {
            boolean shouldSend = false;

            if (executor.getException() != null) {
                executor = executorService.handleFailedExecutionFromExecutor(executor, executor.getException());
                shouldSend = true;
            } else if (executor.isExecutionUpdated()) {
                shouldSend = true;
            }

            if (!shouldSend) {
                Execution execution = executor.getExecution();

                // purge the trigger: reset scheduler trigger at end
                // IMPORTANT: this is to cover an edge case, execution created for failed trigger didn't have any taskrun so they will arrive directly here.
                // We need to detect that and reset them as they will never reach the reset code later on this method.
                if (execution.getTrigger() != null && execution.getState().isFailed() && ListUtils.isEmpty(execution.getTaskRunList())) {
                    sendTriggerExecutionTerminated(execution);
                    this.followExecutionEventQueue.emit(new FollowExecutionEvent(execution, ExecutionEventType.TERMINATED));
                    emitExecutionStatistic(execution);
                }

                return;
            }

            if (log.isDebugEnabled()) {
                executorService.log(log, false, executor);
            }

            // the terminated state can come from the execution queue, in this case we always have a flow in the executor
            // or from a worker task in an afterExecution block, in this case we need to load the flow
            if (executor.getFlow() == null && executor.getExecution().getState().isTerminated()) {
                var execution = executor.getExecution();
                FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                executor = executor.withFlow(flow);
            }
            boolean isTerminated = executor.getFlow() != null && executionService.isTerminated(executor.getFlow(), executor.getExecution());

            Execution execution = executor.getExecution();
            // Fire flow triggers for every distinct state transition that occurred in this cycle.
            // A single cycle can advance through multiple states (e.g. PAUSED → RUNNING → SUCCESS
            // when a pause-resume delay fires and the executor immediately completes the next task).
            // Iterating stateTransitions[1..n] ensures each intermediate state reaches the trigger
            // pipeline, regardless of how many transitions collapsed into one executor cycle.
            List<State.Type> transitions = executor.getStateTransitions();
            for (int i = 1; i < transitions.size(); i++) {
                State.Type transitionState = transitions.get(i);
                processFlowTriggers(transitionState == execution.getState().getCurrent() ? execution : execution.withState(transitions.get(i)));
            }

            // IMPORTANT: this must be done before emitting the last execution message so that all consumers are notified that the execution ends.
            if (isTerminated) {
                // release the concurrency slots (a no-op when no limit applies to the flow),
                // then check if there exists a queued execution and submit it to the execution queue.
                // Transactional outbox: the processor pops inside the concurrency-limit
                // store's transaction and only returns the execution; it is emitted here,
                // after releaseThenPop() has committed (same rule as executionDelayLoop).
                // This runs first in the terminal block on purpose: only the cycle that terminated
                // the execution may release.
                // An execution that was not already terminal when this cycle started cannot have
                // been over before it: afterExecution tasks run once the execution state is already terminal,
                // and the cycle completing them is the one that really terminates the execution.
                Execution executionAtEntry = executor.getTerminalExecutionAtEntry();
                boolean terminatedByThisCycle = executionAtEntry == null
                    || !executionService.isTerminated(executor.getFlow(), executionAtEntry);
                Optional<Execution> popped = concurrencySlotReleaseProcessor.release(executor, terminatedByThisCycle);
                if (popped.isPresent()) {
                    executionQueue.emit(popped.get());

                    // process flow triggers to allow listening on RUNNING state after a QUEUED state
                    processFlowTriggers(popped.get());
                }

                // if there is a parent, we send a subflow execution result to it
                if (ExecutableUtils.isSubflow(execution)) {
                    // locate the parent execution to find the parent task run
                    String parentExecutionId = (String) execution.getTrigger().getVariables().get("executionId");
                    String taskRunId = (String) execution.getTrigger().getVariables().get("taskRunId");
                    String taskId = (String) execution.getTrigger().getVariables().get("taskId");
                    SubflowExecutionEnd subflowExecutionEnd = new SubflowExecutionEnd(executor.getExecution(), parentExecutionId, taskRunId, taskId, execution.getState().getCurrent());
                    this.subflowExecutionEndQueue.emit(subflowExecutionEnd);
                }

                // if it was a loop execution, we send a terminated loop execution message to the parent execution
                if (executor.getExecution().getKind() == ExecutionKind.LOOP) {
                    var loop = (Loop) executor.getFlow().findTaskByTaskId(executor.getExecution().getLoopRun().taskId());
                    Map<String, Object> outputs = null;
                    if (!ListUtils.isEmpty(loop.getOutputs())) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        try {
                            outputs = loop.computeIterationOutput(runContext, execution);
                        } catch (Exception e) {
                            Logs.logExecution(
                                executor.getExecution(),
                                Level.ERROR,
                                "Failed to render output values",
                                e
                            );
                            runContext.logger().error("Failed to render output values: {}", e.getMessage(), e);
                            execution = execution.withState(State.Type.FAILED);
                            // Persist the FAILED state so the sub-execution is correctly reflected in the DB.
                            try {
                                executionStateStore.lock(
                                    execution.getId(), exec -> new ExecutorContext(exec).withExecution(exec.withState(State.Type.FAILED), "failedOutputRender")
                                );
                            } catch (Exception persistException) {
                                log.error("Failed to persist FAILED state for loop sub-execution {}", execution.getId(), persistException);
                            }
                            executor = executor.withExecution(execution, "failedOutputRender");
                        }

                    }
                    loopExecutionEventQueue.emit(new LoopExecutionEvent(execution.getLoopRun(), execution.getId(), execution.getState().getCurrent(), outputs));
                }

                // purge SLA monitors
                if (!ListUtils.isEmpty(executor.getFlow().getSla()) && executor.getFlow().getSla().stream().anyMatch(ExecutionMonitoringSLA.class::isInstance)) {
                    slaMonitorStateStore.purge(executor.getExecution().getId());
                }

                // purge the trigger: reset scheduler trigger at end
                if (execution.getTrigger() != null && !isRealtimeTriggerExecution(executor.getFlow(), execution)) {
                    sendTriggerExecutionTerminated(execution);
                }

                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                // Note that we must use 'emit' here and not emitAsync as we need to emit it inside the same transaction to avoid races,
                // and transactions are bound to a thread. This is true for all emission of the follow execution event inside an execution lock.
                this.followExecutionEventQueue.emit(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED));

                emitExecutionStatistic(execution);
            } else {
                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                this.followExecutionEventQueue.emit(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED));
            }
        } catch (QueueException | FlowNotFoundException | InternalException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it.
                // Persist the FAILED state first, then emit the queue events
                // only after the transaction commits to avoid potential race conditions inside the follow endpoint.
                Optional<ExecutorContext> failedExecutorOpt = executionStateStore.lock(
                    executor.getExecution().getId(), execution ->
                    {
                        Execution failed = execution.failedExecutionFromExecutor(e).execution().withState(State.Type.FAILED);
                        return new ExecutorContext(execution).withExecution(failed, "toExecutionFailure");
                    }
                );

                if (failedExecutorOpt.isPresent()) {
                    Execution failedExecution = failedExecutorOpt.get().getExecution();
                    try {
                        this.executionEventQueue.emit(new ExecutionEvent(failedExecution, ExecutionEventType.TERMINATED));

                        // update all execution followers
                        this.followExecutionEventQueue.emit(new FollowExecutionEvent(failedExecution, ExecutionEventType.TERMINATED));

                        emitExecutionStatistic(failedExecution);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", failedExecution.getId(), ex);
                    }
                }
            }
        }
    }

    /**
     * Asynchronously emits a raw execution-statistic row for the indexer to persist for every terminal NORMAL-kind execution.
     */
    private void emitExecutionStatistic(Execution execution) {
        if (execution.getKind() == null || ExecutionKind.NORMAL == execution.getKind()) {
            // An end date should always be set, but use the current date as a safety belt
            Instant bucket = execution.getState().getEndDate().orElse(Instant.now()).truncatedTo(ChronoUnit.MINUTES);
            this.executionStatisticQueue.emitAsync(new ExecutionStatistic(execution, bucket));
        }
    }

    private void sendTriggerExecutionTerminated(Execution execution) {
        // The scheduler didn't manage states for the WebHook and the Flow trigger
        if (!execution.getTrigger().getType().equals(Webhook.class.getName()) &&
            !execution.getTrigger().getType().equals(io.kestra.plugin.core.trigger.Flow.class.getName()) &&
            !execution.getTrigger().getType().equals(io.kestra.plugin.core.flow.Subflow.class.getName())
        ) {
            TriggerId triggerId = TriggerId.of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getTrigger().getId());
            triggerEventQueue.send(new TriggerExecutionTerminated(triggerId, execution.getId(), execution.getState().getCurrent()));
        }
    }

    /**
     * A realtime trigger's lock spans the trigger's whole lifetime on the worker, not a single execution.
     * Terminations of the executions it emits must not send {@link TriggerExecutionTerminated}, otherwise the
     * scheduler would unlock and resubmit a trigger that is still running. The trigger-creation failure path
     * (FAILED execution with no task run) bypasses this check and remains the termination signal.
     */
    static boolean isRealtimeTriggerExecution(FlowWithSource flow, Execution execution) {
        if (flow == null || flow.getTriggers() == null) {
            return false;
        }
        for (AbstractTrigger trigger : flow.getTriggers()) {
            if (trigger.getId().equals(execution.getTrigger().getId())) {
                return TriggerType.REALTIME.equals(TriggerType.from(trigger));
            }
        }
        return false;
    }

    private void processFlowTriggers(Execution execution) throws QueueException {
        flowTriggerProcessingTimer.record(throwRunnable(() ->
        {
            Collection<FlowWithSource> allFlows = flowMetaStore.allLastVersion();

            // directly process simple conditions
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> f.getFlow())
                .distinct() // as computeExecutionsFromFlowTriggers is based on flow, we must map FlowWithFlowTrigger to a flow and distinct to avoid multiple execution for the same flow
                .flatMap(f -> flowTriggerService.computeExecutionsFromFlowTriggerConditions(execution, f).stream())
                .forEach(throwConsumer(exec -> executionQueue.emit(exec)));

            // send multiple conditions to the multiple condition queue for later processing
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> !ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> new MultipleConditionEvent(f.getFlow(), execution))
                .distinct() // we can have multiple MultipleConditionEvent if a flow contains multiple triggers as it would lead to multiple FlowWithFlowTrigger
                .forEach(throwConsumer(multipleCondition -> multipleConditionEventQueue.emit(multipleCondition)));
        }));
    }

    @Override
    protected ServiceState doStop() {
        // AbstractService.stop() already waited for any in-flight startup, so nothing can be
        // created past this point; the loops and watchers see the stop via isStopRequested().
        try {
            this.receiveCancellations.forEach(Runnable::run);
            this.queueSubscribers.forEach(QueueSubscriber::close);
        } finally {
            // Always stop the scheduled loops: leaving them running after the context is closed makes
            // them fail on the closed datasource and escalate to an application shutdown.
            // The futures are null when stop ran before run() scheduled them.
            ExecutorsUtils.closeScheduledThreadPool(
                scheduledExecutorService,
                Duration.ofSeconds(5),
                Stream.of(executionDelayFuture, monitorSLAFuture).filter(Objects::nonNull).toList()
            );
        }
        return ServiceState.TERMINATED_GRACEFULLY;
    }
}
