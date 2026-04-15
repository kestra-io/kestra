package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.*;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.*;
import io.kestra.core.utils.*;
import io.kestra.executor.handler.*;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.trigger.Webhook;

import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import static io.kestra.core.utils.Rethrow.*;

@Singleton
@Slf4j
public class DefaultExecutor extends AbstractService implements Executor {
    private static final String UNABLE_TO_DESERIALIZE_AN_EXECUTION = "Unable to deserialize an execution: {}";
    private static final String IGNORING_EXECUTION_MSG = "Ignoring execution {} because there is a kill switch on it";
    private static final String CANCELLING_EXECUTION_MSG = "Cancelling execution {} because there is a kill switch on it";
    private static final String KILLING_EXECUTION_MSG = "Killing execution {} because there is a kill switch on it";

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;
    @Inject
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    @Inject
    private DispatchQueueInterface<ExecutionEvent> executionEventQueue;
    @Inject
    private BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue;
    @Inject
    private DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;
    @Inject
    private BroadcastQueueInterface<ExecutionKilled> killQueue;
    @Inject
    private DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    @Inject
    private DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;
    @Inject
    private DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue;
    @Inject
    private DispatchQueueInterface<TerminatedLoopExecution> terminatedLoopExecutionQueue;
    @Inject
    private KillSwitchService killSwitchService;
    @Inject
    private ExecutorService executorService;
    @Inject
    private ExecutionService executionService;
    @Inject
    private FlowTriggerService flowTriggerService;
    @Inject
    private SLAService slaService;
    @Inject
    private MaintenanceService maintenanceService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private ExecutionStateStore executionStateStore;
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;
    @Inject
    private ExecutionDelayStateStore executionDelayStateStore;
    @Inject
    private SLAMonitorStateStore slaMonitorStateStore;
    @Inject
    private ConcurrencyLimitStateStore concurrencyLimitStateStore;
    @Inject
    private TriggerEventQueue triggerEventQueue;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ExecutionMessageHandler executionMessageHandler;
    @Inject
    private ExecutionCommandMessageHandler executionCommandMessageHandler;
    @Inject
    private ExecutionEventMessageHandler executionEventMessageHandler;
    @Inject
    private WorkerTaskResultMessageHandler workerTaskResultMessageHandler;
    @Inject
    private ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;
    @Inject
    private SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;
    @Inject
    private SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;
    @Inject
    private MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;
    @Inject
    private TerminatedLoopExecutionMessageHandler terminatedLoopExecutionMessageHandler;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> executionDelayFuture;
    private ScheduledFuture<?> monitorSLAFuture;

    private final List<Runnable> receiveCancellations = new ArrayList<>();
    private final List<QueueSubscriber<?>> queueSubscribers = new ArrayList<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final java.util.concurrent.ExecutorService workerTaskResultExecutorService;
    private final java.util.concurrent.ExecutorService executionExecutorService;
    private final int numberOfThreads;

    private Timer flowTriggerProcessingTimer;
    private Timer slaMonitorLoopTimer;
    private Timer executionDelayLoopTimer;
    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    public DefaultExecutor(ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher, ExecutorsUtils executorsUtils, @Value("${kestra.executor.thread-count:0}") int threadCount) {
        super(ServiceType.EXECUTOR, eventPublisher);

        // By default, we start available processors count threads with a minimum of 4 by executor service
        // for the worker task result queue and the execution queue.
        // Other queues would not benefit from more consumers.
        this.numberOfThreads = threadCount != 0 ? threadCount : Math.max(4, KestraContext.getContext().getAllocatedCpuCores());
        this.workerTaskResultExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-worker-task-result-executor");
        this.executionExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-execution-event-executor");

        setState(ServiceState.CREATED);
    }

    @PostConstruct
    void initMetrics() {
        // create metrics to store thread count
        this.metricRegistry.gauge(MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT, MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT_DESCRIPTION, numberOfThreads);

        // init internal timers
        this.flowTriggerProcessingTimer = this.metricRegistry.timer(MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION, MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION_DESCRIPTION);
        this.slaMonitorLoopTimer = this.metricRegistry.timer(MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION_DESCRIPTION);
        this.executionDelayLoopTimer = this.metricRegistry.timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION_DESCRIPTION);
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
                List<CompletableFuture<Void>> futures = workerTaskResults.stream()
                    .map(workerTaskResult -> CompletableFuture.runAsync(() -> workerTaskResultQueue(workerTaskResult), workerTaskResultExecutorService))
                    .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        ));
        this.queueSubscribers.addFirst(this.executionCommandQueue.subscriber().subscribe(this::executionCommandQueue));
        this.queueSubscribers.addFirst(this.subflowExecutionResultQueue.subscriber().subscribe(this::subflowExecutionResultQueue));
        this.queueSubscribers.addFirst(this.subflowExecutionEndQueue.subscriber().subscribe(this::subflowExecutionEndQueue));
        this.queueSubscribers.addFirst(this.multipleConditionEventQueue.subscriber().subscribe(this::multipleConditionEventQueue));
        this.queueSubscribers.addFirst(this.terminatedLoopExecutionQueue.subscriber().subscribe(this::loopExecutionTerminatedQueue));
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
                    // We avoid closing the Executor if the exception is a CannotCreateTransactionException as it may be transient
                    if (e.getCause() != null && !e.getCause().getClass().getSimpleName().equals("CannotCreateTransactionException")) {
                        log.error("Executor fatal exception in the scheduledDelay thread", e);
                        close();
                        KestraContext.getContext().shutdown();
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
                    // We avoid closing the Executor if the exception is a CannotCreateTransactionException as it may be transient
                    if (e.getCause() != null && !e.getCause().getClass().getSimpleName().equals("CannotCreateTransactionException")) {
                        log.error("Executor fatal exception in the scheduledSLAMonitor thread", e);
                        close();
                        KestraContext.getContext().shutdown();
                    }
                }
            }
        );

        // init the service
        if (this.maintenanceService.isInMaintenanceMode()) {
            enterMaintenance();
        } else {
            setState(ServiceState.RUNNING);
        }
        log.info("Executor started with {} thread(s)", numberOfThreads);
    }

    // The execution queue is used to send newly created executions, so the first step is to create the execution inside the database
    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();

        try {
            // we create the execution even if skipped, so it is at least present in the DB
            executionStateStore.create(message);
        } catch (Exception e) {
            log.error("Unable to create execution {}", message.getId(), e);
        }

        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType.isKillSwitched(message)) {
            handleKillSwitchedExecution(evaluationType, message);
            return;
        }

        // TODO investigate calling the ExecutionEventMessageHandler here to avoid a loop inside the executor event queue
        Optional<ExecutorContext> maybeExecutor = executionMessageHandler.handle(message);
        maybeExecutor.ifPresent(this::toExecution);
    }

    private void executionCommandQueue(Either<ExecutionCommand, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        ExecutionCommand message = either.getLeft();
        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType != EvaluationType.PASS) {
            var execution = executionStateStore.findById(message.executionId());
            if (evaluationType.isKillSwitched(execution)) {
                handleKillSwitchedExecution(evaluationType, execution);
                return;
            }
        }

        Optional<ExecutorContext> maybeExecutor = executionCommandMessageHandler.handle(message);
        maybeExecutor.ifPresent(this::toExecution);
    }

    private void executionEventQueue(Either<ExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        ExecutionEvent message = either.getLeft();
        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType != EvaluationType.PASS) {
            var execution = executionStateStore.findById(message.executionId());
            if (evaluationType.isKillSwitched(execution)) {
                handleKillSwitchedExecution(evaluationType, execution);
                return;
            }
        }

        Optional<ExecutorContext> maybeExecutor = executionEventMessageHandler.handle(message);
        maybeExecutor.ifPresent(this::toExecution);
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage(), either.getRight());
            return;
        }

        WorkerTaskResult message = either.getLeft();
        EvaluationType evaluationType = killSwitchService.evaluate(message.getTaskRun());
        if (evaluationType != EvaluationType.PASS) {
            handleKillSwitchedWorkerTaskResult(evaluationType, message);
            return;
        }

        Optional<ExecutorContext> maybeExecutor = workerTaskResultMessageHandler.handle(message);
        maybeExecutor.ifPresent(this::toExecution);
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

        if (killSwitchService.evaluate(killedExecution.getExecutionId()) == EvaluationType.IGNORE) { // we process other types of evaluation
            log.warn(IGNORING_EXECUTION_MSG, killedExecution.getExecutionId());
            return;
        }

        Optional<ExecutorContext> maybeExecutor = executionKilledExecutionMessageHandler.handle(killedExecution);

        // Transmit the new execution state. Note that the execution
        // will eventually transition to KILLED state before sub-flow executions are actually killed.
        // This behavior is acceptable due to the fire-and-forget nature of the killing event.
        maybeExecutor.ifPresent(executor -> this.toExecution(executor, true));
    }

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution result: {}", either.getRight().getMessage());
            return;
        }

        SubflowExecutionResult message = either.getLeft();
        // we filter all messages for which there is a kill switch as the kill switch will apply to the child execution anyway
        if (killSwitchService.evaluate(message.getExecutionId()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution result for child execution {} as there is a kill switch in it", message.getExecutionId());
            return;
        }
        // we filter all messages for which there is a kill switch as the kill switch will apply to the parent execution anyway
        if (killSwitchService.evaluate(message.getParentTaskRun()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution result for parent execution {} as there is a kill switch in it", message.getParentTaskRun().getExecutionId());
            return;
        }

        Optional<ExecutorContext> maybeExecutor = subflowExecutionResultMessageHandler.handle(message);
        maybeExecutor.ifPresent(this::toExecution);
    }

    private void subflowExecutionEndQueue(Either<SubflowExecutionEnd, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution end: {}", either.getRight().getMessage());
            return;
        }

        SubflowExecutionEnd message = either.getLeft();
        // we filter all messages for which there is a kill switch as the kill switch will apply to the child execution anyway
        if (killSwitchService.evaluate(message.childExecution()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution end for child execution {} as there is a kill switch in it", message.childExecution().getId());
            return;
        }
        // we filter all messages for which there is a kill switch as the kill switch will apply to the parent execution anyway
        if (killSwitchService.evaluate(message.parentExecutionId()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution end for parent execution {} as there is a kill switch in it", message.parentExecutionId());
            return;
        }

        subflowExecutionEndMessageHandler.handle(message);
    }

    private void multipleConditionEventQueue(Either<MultipleConditionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a multiple condition event: {}", either.getRight().getMessage());
            return;
        }

        MultipleConditionEvent multipleConditionEvent = either.getLeft();

        multipleConditionEventMessageHandler.handle(multipleConditionEvent);
    }

    private void loopExecutionTerminatedQueue(Either<TerminatedLoopExecution, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a terminated loop execution event: {}", either.getRight().getMessage());
            return;
        }

        TerminatedLoopExecution terminatedLoopExecution = either.getLeft();

        Optional<ExecutorContext> maybeExecutor = terminatedLoopExecutionMessageHandler.handle(terminatedLoopExecution);
        maybeExecutor.ifPresent(this::toExecution);
    }

    private void handleKillSwitchedExecution(EvaluationType evaluationType, Execution message) {
        handleKillSwitchedExecution(evaluationType, message.getTenantId(), message.getId());
    }

    private void handleKillSwitchedWorkerTaskResult(EvaluationType evaluationType, WorkerTaskResult message) {
        handleKillSwitchedExecution(evaluationType, message.getTaskRun().getTenantId(), message.getTaskRun().getExecutionId());
    }

    private void handleKillSwitchedExecution(EvaluationType evaluationType, String tenantId, String executionId) {
        switch (evaluationType) {
            case IGNORE -> log.warn(IGNORING_EXECUTION_MSG, executionId);
            case KILL -> {
                log.warn(KILLING_EXECUTION_MSG, executionId);
                killExecution(tenantId, executionId);
            }
            case CANCEL -> {
                log.warn(CANCELLING_EXECUTION_MSG, executionId);
                cancelExecution(executionId);
            }
        }
    }

    private void killExecution(String tenantId, String executionId) {
        executionStateStore.lock(executionId, execution ->
        {
            if (!execution.getState().isTerminated()) {
                var newExecution = execution.withState(State.Type.KILLING).addLabel(new Label(Label.KILL_SWITCH, "killed"));
                return new ExecutorContext(newExecution);
            }
            return null;
        });

        try {
            killQueue.emit(
                ExecutionKilledExecution.builder()
                    .tenantId(tenantId)
                    .executionId(executionId)
                    .isOnKillCascade(true)
                    .state(ExecutionKilled.State.REQUESTED)
                    .build()
            );
        } catch (QueueException e) {
            log.error("Unable to kill the execution {}", executionId, e);
        }
    }

    private void cancelExecution(String executionId) {
        executionStateStore.lock(executionId, execution ->
        {
            if (!execution.getState().isTerminated()) {
                var newExecution = execution.withState(State.Type.CANCELLED).addLabel(new Label(Label.KILL_SWITCH, "cancelled"));
                return new ExecutorContext(newExecution);
            }
            return null;
        });
    }

    /**
     * ExecutionDelay is currently two types of execution:
     * <br/>
     * - Paused flow that will be restarted after an interval/timeout
     * <br/>
     * - Failed flow that will be retried after an interval
     **/
    private void executionDelayLoop() {
        if (this.shutdown.get() || this.isPaused.get()) {
            return;
        }

        executionDelayLoopTimer.record(() -> {
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
                        if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW) && !execution.getState().isTerminated()) {
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
                        // Handle failed task retries
                        else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)) {
                            FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                            Execution newAttempt = executionService.retryTask(
                                execution,
                                flow,
                                executionDelay.getTaskRunId()
                            );
                            executor = executor.withExecution(newAttempt, "retryFailedTask");
                        }
                        // Handle failed flow retries
                        else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)) {
                            FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
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

                maybeExecutor.ifPresent(this::toExecution);
            });
        });
    }

    private void executionSLAMonitorLoop() {
        if (this.shutdown.get() || this.isPaused.get()) {
            return;
        }

        slaMonitorLoopTimer.record(() -> {
            slaMonitorStateStore.processExpired(Instant.now(), slaMonitor ->
            {
                Optional<ExecutorContext> maybeExecutor = executionStateStore.lock(slaMonitor.getExecutionId(), execution ->
                {
                    FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
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
                                    MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution())
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
                FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                executor = executor.withFlow(flow);
            }
            boolean isTerminated = executor.getFlow() != null && executionService.isTerminated(executor.getFlow(), executor.getExecution());

            Execution execution = executor.getExecution();
            // handle flow triggers on state change
            if (!execution.getState().getCurrent().equals(executor.getOriginalState())) {
                processFlowTriggers(execution);
            }

            // IMPORTANT: this must be done before emitting the last execution message so that all consumers are notified that the execution ends.
            // NOTE: we may also purge ExecutionKilled events, but as there may not be a lot of them, it may not be worth it.
            if (isTerminated) {
                // if there is a parent, we send a subflow execution result to it
                if (ExecutableUtils.isSubflow(execution)) {
                    // locate the parent execution to find the parent task run
                    String parentExecutionId = (String) execution.getTrigger().getVariables().get("executionId");
                    String taskRunId = (String) execution.getTrigger().getVariables().get("taskRunId");
                    String taskId = (String) execution.getTrigger().getVariables().get("taskId");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> outputs = (Map<String, Object>) execution.getTrigger().getVariables().get("taskRunOutputs");
                    SubflowExecutionEnd subflowExecutionEnd = new SubflowExecutionEnd(
                        executor.getExecution(), parentExecutionId, taskRunId, taskId, execution.getState().getCurrent(), outputs
                    );
                    this.subflowExecutionEndQueue.emit(subflowExecutionEnd);
                }

                // if it was a loop execution, we send a terminated loop execution message to the parent execution
                if (executor.getExecution().getKind() == ExecutionKind.LOOP) {
                    var loop = (Loop) executor.getFlow().findTaskByTaskId(executor.getExecution().getLoopRun().taskId());
                    Map<String, Object> outputs = null;
                    if (!ListUtils.isEmpty(loop.getOutputs())) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        var inputAndOutput = runContext.inputAndOutput();

                        try {
                            outputs = inputAndOutput.renderOutputs(loop.getOutputs());
                            outputs = inputAndOutput.typedOutputs(loop.getOutputs(), executor.getExecution(), outputs);
                        } catch (Exception e) {
                            Logs.logExecution(
                                executor.getExecution(),
                                Level.ERROR,
                                "Failed to render output values",
                                e
                            );
                            runContext.logger().error("Failed to render output values: {}", e.getMessage(), e);
                            // FIXME should we fail the execution?
                        }
                    }
                    var terminatedLoopExecution = new TerminatedLoopExecution(executor.getExecution().getLoopRun(), executor.getExecution().getId(), executor.getExecution().getState().getCurrent(), outputs);
                    terminatedLoopExecutionQueue.emit(terminatedLoopExecution);
                }

                // purge SLA monitors
                if (!ListUtils.isEmpty(executor.getFlow().getSla()) && executor.getFlow().getSla().stream().anyMatch(ExecutionMonitoringSLA.class::isInstance)) {
                    slaMonitorStateStore.purge(executor.getExecution().getId());
                }

                // check if there exist a queued execution and submit it to the execution queue
                if (executor.getFlow().getConcurrency() != null) {
                    // if an execution was queued but never running, it would have never been counted inside the concurrency limit and should not lead to popping a new queued execution
                    boolean queuedThenKilled = execution.getState().getCurrent() == State.Type.KILLED
                        && execution.getState().getHistories().stream().anyMatch(h -> h.getState().isQueued())
                        && execution.getState().getHistories().stream().noneMatch(h -> h.getState().onlyRunning());
                    // if an execution was FAILED or CANCELLED due to concurrency limit exceeded, it would have never been counter inside the concurrency limit and should not lead to popping a new queued execution
                    boolean concurrencyShortCircuitState = Concurrency.possibleTransitions(execution.getState().getCurrent())
                        && execution.getState().getHistories().get(execution.getState().getHistories().size() - 2).getState().isCreated();
                    // as we may receive multiple time killed execution (one when we kill it, then one for each running worker task), we limit to the first we receive: when the state transitioned from KILLING to KILLED
                    boolean killingThenKilled = execution.getState().getCurrent().isKilled() && executor.getOriginalState() == State.Type.KILLING;
                    if (!queuedThenKilled && !concurrencyShortCircuitState && (!execution.getState().getCurrent().isKilled() || killingThenKilled)) {
                        if (executor.getFlow().getConcurrency().getBehavior() == Concurrency.Behavior.QUEUE) {
                            var finalFlow = executor.getFlow();

                            // Pop the next queued execution atomically with decrement/increment to avoid race conditions
                            // that could leave executions stuck in the queue indefinitely (see issue #13785)
                            concurrencyLimitStateStore.decrementAndPop(
                                finalFlow,
                                executionQueuedStateStore,
                                throwBiConsumer((dslContext, queued) ->
                                {
                                    var newExecution = queued.withState(State.Type.RUNNING);
                                    executionQueue.emit(newExecution);
                                    metricRegistry.counter(
                                        MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT_DESCRIPTION,
                                        metricRegistry.tags(newExecution)
                                    ).increment();

                                    // process flow triggers to allow listening on RUNNING state after a QUEUED state
                                    processFlowTriggers(newExecution);
                                })
                            );
                        } else {
                            int newLimit = concurrencyLimitStateStore.decrement(executor.getFlow());
                            if (newLimit >= executor.getFlow().getConcurrency().getLimit()) {
                                log.error(
                                    "Concurrency limit reached for flow {}.{} after decrementing the execution running count due to the terminated execution {}. This should not happen.",
                                    executor.getFlow().getNamespace(), executor.getFlow().getId(), executor.getExecution().getId()
                                );
                            }
                        }
                    }
                }

                // purge the trigger: reset scheduler trigger at end
                if (execution.getTrigger() != null) {
                    sendTriggerExecutionTerminated(execution);
                }

                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                this.followExecutionEventQueue.emitAsync(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED));
            } else {
                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                this.followExecutionEventQueue.emitAsync(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED));
            }
        } catch (QueueException | FlowNotFoundException | InternalException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it
                executionStateStore.lock(executor.getExecution().getId(), execution ->
                {
                    try {
                        Execution failed = execution.failedExecutionFromExecutor(e).execution().withState(State.Type.FAILED);
                        ExecutionEvent event = new ExecutionEvent(failed, ExecutionEventType.TERMINATED);
                        this.executionEventQueue.emit(event);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", execution.getId(), ex);
                    }
                    return null;
                });
            }
        }
    }

    private void sendTriggerExecutionTerminated(Execution execution) {
        // The scheduler didn't manage states for the WebHook and the Flow trigger
        if (!execution.getTrigger().getType().equals(Webhook.class.getName()) && !execution.getTrigger().getType().equals(io.kestra.plugin.core.trigger.Flow.class.getName())) {
            TriggerId triggerId = TriggerId.of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getTrigger().getId());
            triggerEventQueue.send(new TriggerExecutionTerminated(triggerId, execution.getId(), execution.getState().getCurrent()));
        }
    }

    private void processFlowTriggers(Execution execution) throws QueueException {
        flowTriggerProcessingTimer.record(throwRunnable(() -> {
            Collection<FlowWithSource> allFlows = flowMetaStore.allLastVersion();

            // directly process simple conditions
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> f.getTrigger().getPreconditions() == null && ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> f.getFlow())
                .distinct() // as computeExecutionsFromFlowTriggers is based on flow, we must map FlowWithFlowTrigger to a flow and distinct to avoid multiple execution for the same flow
                .flatMap(f -> flowTriggerService.computeExecutionsFromFlowTriggerConditions(execution, f).stream())
                .forEach(throwConsumer(exec -> executionQueue.emit(exec)));

            // send multiple conditions to the multiple condition queue for later processing
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> f.getTrigger().getPreconditions() != null || !ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> new MultipleConditionEvent(f.getFlow(), execution))
                .distinct() // we can have multiple MultipleConditionEvent if a flow contains multiple triggers as it would lead to multiple FlowWithFlowTrigger
                .forEach(throwConsumer(multipleCondition -> multipleConditionEventQueue.emit(multipleCondition)));
        }));
    }

    @Override
    protected ServiceState doStop() {
        this.receiveCancellations.forEach(Runnable::run);
        this.queueSubscribers.forEach(QueueSubscriber::close);
        ExecutorsUtils.closeScheduledThreadPool(scheduledExecutorService, Duration.ofSeconds(5), List.of(executionDelayFuture, monitorSLAFuture));
        return ServiceState.TERMINATED_GRACEFULLY;
    }
}
