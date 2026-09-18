package io.kestra.executor;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.*;
import io.kestra.core.runners.Executor;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.*;
import io.kestra.executor.configuration.ExecutorConfiguration;
import io.kestra.executor.handler.*;

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
    private final DispatchQueueInterface<ExecutionEvent> executionEventQueue;
    private final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final BroadcastQueueInterface<ExecutionKilled> killQueue;
    private final DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    private final DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;
    private final DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue;
    private final DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue;

    private final MaintenanceService maintenanceService;

    private final MultipleConditionStateStore multipleConditionStateStore;

    private final MetricRegistry metricRegistry;

    // The context captured at construction time.
    // The static context returned by KestraContext.getContext() might change if the context is restarted inside the same JVM
    // which can occur at least in tests.
    private final KestraContext kestraContext;

    private final RunContextFactory runContextFactory;
    private final TaskOutputService taskOutputService;

    private final ExecutionCommandMessageHandler executionCommandMessageHandler;
    private final ExecutionEventMessageHandler executionEventMessageHandler;
    private final WorkerTaskResultMessageHandler workerTaskResultMessageHandler;
    private final ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;
    private final SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;
    private final SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;
    private final MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;
    private final LoopExecutionEventMessageHandler loopExecutionEventMessageHandler;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService scheduledExecutorService;
    private final Clock clock;
    private final ExecutorConfiguration executorConfiguration;
    private final ExecutorCore executorCore;
    private ScheduledFuture<?> executionDelayFuture;
    private ScheduledFuture<?> monitorSLAFuture;
    private ScheduledFuture<?> multipleConditionPurgeFuture;

    // Thread-safe: populated by run() but iterated from maintenance listener and shutdown threads.
    private final List<Runnable> receiveCancellations = new CopyOnWriteArrayList<>();
    private final List<QueueSubscriber<?>> queueSubscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final java.util.concurrent.ExecutorService workerTaskResultExecutorService;
    private final java.util.concurrent.ExecutorService executionExecutorService;
    private final int numberOfThreads;

    private Timer slaMonitorLoopTimer;
    private Timer executionDelayLoopTimer;
    private Timer multipleConditionPurgeLoopTimer;

    @Inject
    public DefaultExecutor(
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        ExecutorsUtils executorsUtils,
        Clock clock,
        ExecutorConfiguration executorConfiguration,
        KestraContext kestraContext,
        DispatchQueueInterface<Execution> executionQueue,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        DispatchQueueInterface<ExecutionEvent> executionEventQueue,
        DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue,
        BroadcastQueueInterface<ExecutionKilled> killQueue,
        DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue,
        DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue,
        DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue,
        DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue,
        ExecutorCore executorCore,
        MaintenanceService maintenanceService,
        MultipleConditionStateStore multipleConditionStateStore,
        SLAMonitorProcessor slaMonitorProcessor,
        ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor,
        TriggerEventQueue triggerEventQueue,
        MetricRegistry metricRegistry,
        RunContextFactory runContextFactory,
        TaskOutputService taskOutputService,
        ExecutionCommandMessageHandler executionCommandMessageHandler,
        ExecutionEventMessageHandler executionEventMessageHandler,
        WorkerTaskResultMessageHandler workerTaskResultMessageHandler,
        ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler,
        SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler,
        SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler,
        MultipleConditionEventMessageHandler multipleConditionEventMessageHandler,
        LoopExecutionEventMessageHandler loopExecutionEventMessageHandler) {
        MetricRegistry metricRegistry) {
        super(ServiceType.EXECUTOR, eventPublisher);
        this.clock = clock;
        this.scheduledExecutorService = executorsUtils.singleThreadScheduledExecutor("executor-loops");

        this.executorConfiguration = executorConfiguration;
        this.kestraContext = kestraContext;
        this.executionQueue = executionQueue;
        this.executionCommandQueue = executionCommandQueue;
        this.executionEventQueue = executionEventQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.killQueue = killQueue;
        this.subflowExecutionResultQueue = subflowExecutionResultQueue;
        this.subflowExecutionEndQueue = subflowExecutionEndQueue;
        this.multipleConditionEventQueue = multipleConditionEventQueue;
        this.loopExecutionEventQueue = loopExecutionEventQueue;
        this.executorCore = executorCore;
        this.maintenanceService = maintenanceService;
        this.multipleConditionStateStore = multipleConditionStateStore;
        this.metricRegistry = metricRegistry;
        this.runContextFactory = runContextFactory;
        this.taskOutputService = taskOutputService;
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
    public void initMetrics() {
        // create metrics to store thread count
        this.metricRegistry.gauge(MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT, MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT_DESCRIPTION, numberOfThreads);

        // init internal timers
        this.slaMonitorLoopTimer = this.metricRegistry.timer(MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_SLA_MONITOR_LOOP_DURATION_DESCRIPTION);
        this.executionDelayLoopTimer = this.metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_LOOP_DURATION_DESCRIPTION);
        this.multipleConditionPurgeLoopTimer = this.metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_MULTIPLE_CONDITION_PURGE_LOOP_DURATION, MetricRegistry.METRIC_EXECUTOR_MULTIPLE_CONDITION_PURGE_LOOP_DURATION_DESCRIPTION);
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
            executorConfiguration.executionDelayLoopPeriodicityMs(),
            TimeUnit.MILLISECONDS
        );
        monitorSLAFuture = scheduledExecutorService.scheduleAtFixedRate(
            this::executionSLAMonitorLoop,
            0,
            executorConfiguration.monitorSLALoopPeriodicityMs(),
            TimeUnit.MILLISECONDS
        );
        multipleConditionPurgeFuture = scheduledExecutorService.scheduleAtFixedRate(
            this::multipleConditionPurgeLoop,
            0,
            executorConfiguration.multipleConditionPurgeLoopPeriodicityMs(),
            TimeUnit.MILLISECONDS
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

        // look at exceptions on the scheduledMultipleConditionPurge thread
        Thread.ofVirtual().name("executor-multiple-condition-purge-exception-watcher").start(
            () ->
            {
                Await.until(multipleConditionPurgeFuture::isDone);

                try {
                    multipleConditionPurgeFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    // An exception during shutdown is teardown noise (e.g. closed datasource), not a reason to escalate.
                    // We avoid closing the Executor if the exception is a CannotCreateTransactionException as it may be transient
                    if (!isStopRequested() && e.getCause() != null && !e.getCause().getClass().getSimpleName().equals("CannotCreateTransactionException")) {
                        log.error("Executor fatal exception in the scheduledMultipleConditionPurge thread", e);
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
        executorCore.onExecution(either.getLeft());
    }

    private void executionCommandQueue(Either<ExecutionCommand, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }
        executorCore.onExecutionCommand(either.getLeft());
    }

    private void executionEventQueue(Either<ExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }
        executorCore.onExecutionEvent(either.getLeft());
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage(), either.getRight());
            return;
        }
        executorCore.onWorkerTaskResult(either.getLeft());
    }

    private void killQueue(Either<ExecutionKilled, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a killed execution: {}", either.getRight().getMessage());
            return;
        }
        executorCore.onExecutionKilled(either.getLeft());
    }

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution result: {}", either.getRight().getMessage());
            return;
        }
        executorCore.onSubflowExecutionResult(either.getLeft());
    }

    private void subflowExecutionEndQueue(Either<SubflowExecutionEnd, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution end: {}", either.getRight().getMessage());
            return;
        }
        executorCore.onSubflowExecutionEnd(either.getLeft());
    }

    private void multipleConditionEventQueue(Either<MultipleConditionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a multiple condition event: {}", either.getRight().getMessage());
            return;
        }
        executorCore.onMultipleConditionEvent(either.getLeft());
    }

    private void loopExecutionEventQueue(Either<LoopExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a loop execution event: {}", either.getRight().getMessage());
            return;
        }
        executorCore.onLoopExecutionEvent(either.getLeft());
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

        executionDelayLoopTimer.record(() -> executorCore.onExpiredExecutionDelays(clock.instant()));
    }

    private void executionSLAMonitorLoop() {
        if (isStopRequested() || this.isPaused.get()) {
            return;
        }

        slaMonitorLoopTimer.record(() -> executorCore.onExpiredSLAMonitors(clock.instant()));
    }

    private void multipleConditionPurgeLoop() {
        if (isStopRequested() || this.isPaused.get()) {
            return;
        }

        multipleConditionPurgeLoopTimer.record(() -> multipleConditionStateStore.purgeExpired(clock.instant()));
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
                Stream.of(executionDelayFuture, monitorSLAFuture, multipleConditionPurgeFuture).filter(Objects::nonNull).toList()
            );
        }
        return ServiceState.TERMINATED_GRACEFULLY;
    }
}
