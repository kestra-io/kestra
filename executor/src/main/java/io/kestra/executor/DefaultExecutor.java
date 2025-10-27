package io.kestra.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.runners.Executor;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.*;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.*;
import io.kestra.plugin.core.flow.ForEachItem;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.*;

@Singleton
@Slf4j
public class DefaultExecutor implements Executor {
    private static final ObjectMapper MAPPER = ExecutorMapper.of();
    public static final String UNABLE_TO_DESERIALIZE_AN_EXECUTION = "Unable to deserialize an execution: {}";
    public static final String SKIPPING_EXECUTION = "Skipping execution {}";

    @Inject
    private ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_EVENT_NAMED)
    private QueueInterface<ExecutionEvent> executionEventQueue;
    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private QueueInterface<WorkerJob> workerJobQueue;
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;
    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    private QueueInterface<ExecutionKilled> killQueue;
    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONEND_NAMED)
    private QueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;
    @Inject
    @Named(QueueFactoryInterface.MULTIPLE_CONDITION_EVENT_NAMED)
    private QueueInterface<MultipleConditionEvent> multipleConditionEventQueue;
    @Inject
    @Named(QueueFactoryInterface.CLUSTER_EVENT_NAMED)
    private Optional<QueueInterface<ClusterEvent>> clusterEventQueue;
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<FlowInterface> flowQueue;

    @Inject
    private SkipExecutionService skipExecutionService;
    @Inject
    private PluginDefaultService pluginDefaultService;
    @Inject
    private ExecutorService executorService;
    @Inject
    private WorkerGroupService workerGroupService;
    @Inject
    private ExecutionService executionService;
    @Inject
    private VariablesService variablesService;
    @Inject
    private FlowTriggerService flowTriggerService;
    @Inject
    private SLAService slaService;
    @Inject
    private MaintenanceService maintenanceService;
    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private ExecutionStateStore executionStateStore;
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;
    @Inject
    private MultipleConditionStorageInterface multipleConditionStorage;
    @Inject
    private ExecutionDelayStateStore executionDelayStateStore;
    @Inject
    private SLAMonitorStateStore  slaMonitorStateStore;
    @Inject
    private ConcurrencyLimitStateStore concurrencyLimitStateStore;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowListenersInterface flowListeners;

    @Value("${kestra.executor.clean.execution-queue:true}")
    private boolean cleanExecutionQueue;
    @Value("${kestra.executor.clean.worker-queue:true}")
    private boolean cleanWorkerJobQueue;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> executionDelayFuture;
    private ScheduledFuture<?> monitorSLAFuture;

    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final String id = IdUtils.create();
    private final List<Runnable> receiveCancellations = new ArrayList<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    protected List<FlowWithSource> allFlows;

    private final Tracer tracer;
    private final java.util.concurrent.ExecutorService workerTaskResultExecutorService;
    private final java.util.concurrent.ExecutorService executionExecutorService;
    private final int numberOfThreads;


    @Inject
    public DefaultExecutor(TracerFactory tracerFactory, ExecutorsUtils executorsUtils, @Value("${kestra.executor.thread-count:0}") int threadCount) {
        this.tracer = tracerFactory.getTracer(DefaultExecutor.class, "EXECUTOR");

        // By default, we start available processors count threads with a minimum of 4 by executor service
        // for the worker task result queue and the execution queue.
        // Other queues would not benefit from more consumers.
        this.numberOfThreads = threadCount != 0 ? threadCount : Math.max(4, Runtime.getRuntime().availableProcessors());
        this.workerTaskResultExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-worker-task-result-executor");
        this.executionExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "executor-execution-event-executor");
    }

    @PostConstruct
    void initMetrics() {
        // create metrics to store thread count
        this.metricRegistry.gauge(MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT, MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT_DESCRIPTION, numberOfThreads);
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
        setState(ServiceState.CREATED);

        // listen to all flows and make sure we receive them before listening to other queues
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);
        try {
            Await.until(() -> this.allFlows != null, Duration.ofMillis(100), Duration.ofMinutes(5));
        } catch (TimeoutException e) {
            log.error("Executor fatal exception: cannot get all flows after 5mn", e);
            close();
            KestraContext.getContext().shutdown();
            return;
        }

        // listen to executor related queues
        this.receiveCancellations.addFirst(this.flowQueue.receive(FlowTopology.class, this::flowQueue)); // TODO it should not be there...
        this.receiveCancellations.addFirst(this.executionQueue.receive(Executor.class, this::executionQueue));
        this.receiveCancellations.addFirst(this.executionEventQueue.receiveBatch(
            Executor.class,
            executionEvents -> {
                List<CompletableFuture<Void>> futures = executionEvents.stream()
                    .map(executionEvent -> CompletableFuture.runAsync(() -> executionEventQueue(executionEvent), executionExecutorService))
                    .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        ));
        this.receiveCancellations.addFirst(this.workerTaskResultQueue.receiveBatch(
            Executor.class,
            workerTaskResults -> {
                List<CompletableFuture<Void>> futures = workerTaskResults.stream()
                    .map(workerTaskResult -> CompletableFuture.runAsync(() -> workerTaskResultQueue(workerTaskResult), workerTaskResultExecutorService))
                    .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        ));
        this.receiveCancellations.addFirst(this.killQueue.receive(Executor.class, this::killQueue));
        this.receiveCancellations.addFirst(this.subflowExecutionResultQueue.receive(Executor.class, this::subflowExecutionResultQueue));
        this.receiveCancellations.addFirst(this.subflowExecutionEndQueue.receive(Executor.class, this::subflowExecutionEndQueue));
        this.receiveCancellations.addFirst(this.multipleConditionEventQueue.receive(Executor.class, this::multipleConditionEventQueue));
        this.clusterEventQueue.ifPresent(clusterEventQueueInterface -> this.receiveCancellations.addFirst(clusterEventQueueInterface.receive(this::clusterEventQueue)));

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
            () -> {
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
            () -> {
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
        setState(ServiceState.RUNNING);
        log.info("Executor started with {} thread(s)", numberOfThreads);
    }

    private void flowQueue(Either<FlowInterface, DeserializationException> either) {
        FlowInterface flow;
        if (either.isRight()) {
            log.error("Unable to deserialize a flow: {}", either.getRight().getMessage());
            try {
                var jsonNode = MAPPER.readTree(either.getRight().getRecord());
                flow = FlowWithException.from(jsonNode, either.getRight()).orElseThrow(IOException::new);
            } catch (IOException e) {
                // if we cannot create a FlowWithException, ignore the message
                log.error("Unexpected exception when trying to handle a deserialization error", e);
                return;
            }
        } else {
            flow = either.getLeft();
        }

        try {
            flowTopologyRepository.save(
                flow,
                (flow.isDeleted() ?
                    Stream.<FlowTopology>empty() :
                    flowTopologyService
                        .topology(
                            pluginDefaultService.injectVersionDefaults(flow, true),
                            this.allFlows.stream().filter(f -> Objects.equals(f.getTenantId(), flow.getTenantId())).toList()
                        )
                )
                    .distinct()
                    .toList()
            );
        } catch (Exception e) {
            log.error("Unable to save flow topology for flow {}", flow.uid(), e);
        }
    }

    // This serves as a temporal bridge between the old execution queue and the new execution event queue to avoid updating all code that uses the old queue
    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();
        if (skipExecutionService.skipExecution(message)) {
            log.warn(SKIPPING_EXECUTION, message.getId());
            return;
        }

        try {
            executionEventQueue.emit(new ExecutionEvent(message, ExecutionEventType.CREATED));
        } catch (QueueException e) {
            // If we cannot send the execution event, we fail the execution
            executionStateStore.lock(message.getId(), execution -> {
                try {
                    Execution failed = execution.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED);
                    ExecutionEvent event = new ExecutionEvent(failed, ExecutionEventType.TERMINATED);
                    this.executionEventQueue.emit(event);
                    return new ExecutorContext(failed);
                } catch (QueueException ex) {
                    log.error("Unable to emit the execution {}", execution.getId(), ex);
                }
                return null;
            });
        }
    }

    private void executionEventQueue(Either<ExecutionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error(UNABLE_TO_DESERIALIZE_AN_EXECUTION, either.getRight().getMessage());
            return;
        }

        ExecutionEvent message = either.getLeft();
        if (skipExecutionService.skipExecution(message)) {
            log.warn(SKIPPING_EXECUTION, message.executionId());
            return;
        }

        ExecutorContext result = executionStateStore.lock(message.executionId(), execution -> tracer.inCurrentContext(
            execution,
            FlowId.uidWithoutRevision(execution),
            () -> {
                try {
                    final FlowWithSource flow = findFlow(execution);
                    ExecutorContext executor = new ExecutorContext(execution, flow);

                    // schedule it for later if needed
                    if (execution.getState().getCurrent() == State.Type.CREATED && execution.getScheduleDate() != null && execution.getScheduleDate().isAfter(Instant.now())) {
                        ExecutionDelay executionDelay = ExecutionDelay.builder()
                            .executionId(executor.getExecution().getId())
                            .date(execution.getScheduleDate())
                            .state(State.Type.RUNNING)
                            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                            .build();
                        executionDelayStateStore.save(executionDelay);
                        return executor;
                    }

                    // create an SLA monitor if needed
                    if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted()) && !ListUtils.isEmpty(flow.getSla())) {
                        List<SLAMonitor> monitors = flow.getSla().stream()
                            .filter(ExecutionMonitoringSLA.class::isInstance)
                            .map(ExecutionMonitoringSLA.class::cast)
                            .map(sla -> SLAMonitor.builder()
                                .executionId(execution.getId())
                                .slaId(((SLA) sla).getId())
                                .deadline(execution.getState().getStartDate().plus(sla.getDuration()))
                                .build()
                            )
                            .toList();
                        monitors.forEach(monitor -> slaMonitorStateStore.save(monitor));
                    }

                    // handle concurrency limit, we need to use a different queue to be sure that execution running
                    // are processed sequentially so inside a queue with no parallelism
                    if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted()) && flow.getConcurrency() != null) {
                        ExecutionRunning executionRunning = ExecutionRunning.builder()
                            .tenantId(executor.getFlow().getTenantId())
                            .namespace(executor.getFlow().getNamespace())
                            .flowId(executor.getFlow().getId())
                            .execution(executor.getExecution())
                            .concurrencyState(ExecutionRunning.ConcurrencyState.CREATED)
                            .build();

                        ExecutionRunning processed = concurrencyLimitStateStore.countThenProcess(flow, (txContext, concurrencyLimit) -> {
                            ExecutionRunning computed = executorService.processExecutionRunning(flow, concurrencyLimit.getRunning(), executionRunning.withExecution(execution)); // be sure that the execution running contains the latest value of the execution
                            if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.RUNNING && !computed.getExecution().getState().isTerminated()) {
                                return Pair.of(computed, concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1));
                            } else if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                executionQueuedStateStore.save(txContext, ExecutionQueued.fromExecutionRunning(computed));
                            }
                            return Pair.of(computed, concurrencyLimit);
                        });

                        // if the execution is queued or terminated due to concurrency limit, we stop here
                        if (processed.getExecution().getState().isTerminated() || processed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                            return executor.withExecution(processed.getExecution(), "handleConcurrencyLimit");
                        }
                    }

                    // handle execution changed SLA
                    executor = executorService.handleExecutionChangedSLA(executor);

                    // process the execution
                    if (log.isDebugEnabled()) {
                        executorService.log(log, true, executor);
                    }
                    executor = executorService.process(executor);

                    if (!executor.getNexts().isEmpty()) {
                        executor.withExecution(
                            executorService.onNexts(executor.getExecution(), executor.getNexts()),
                            "onNexts"
                        );
                    }

                    // worker task
                    if (!executor.getWorkerTasks().isEmpty()) {
                        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();
                        executor
                            .getWorkerTasks()
                            .forEach(throwConsumer(workerTask -> {
                                try {
                                    if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                                        workerTaskResults.add(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.SKIPPED)));
                                    } else {
                                        if (workerTask.getTask().isSendToWorkerTask()) {
                                            Optional<WorkerGroup> maybeWorkerGroup = workerGroupService.resolveGroupFromJob(flow, workerTask);
                                            String workerGroupKey = maybeWorkerGroup.map(throwFunction(workerGroup -> workerTask.getRunContext().render(workerGroup.getKey())))
                                                .orElse(null);
                                            if (workerTask.getTask() instanceof WorkingDirectory) {
                                                // WorkingDirectory is a flowable so it will be moved to RUNNING a few lines under
                                                workerJobQueue.emit(workerGroupKey, workerTask);
                                            } else {
                                                TaskRun taskRun = workerTask.getTaskRun().withState(State.Type.SUBMITTED);
                                                workerJobQueue.emit(workerGroupKey, workerTask.withTaskRun(taskRun));
                                                workerTaskResults.add(new WorkerTaskResult(taskRun));
                                            }
                                            // flowable attempt state transition to running
                                            if (workerTask.getTask().isFlowable()) {
                                                TaskRun updatedTaskRun = workerTask.getTaskRun()
                                                    .withAttempts(
                                                        List.of(
                                                            TaskRunAttempt.builder()
                                                                .state(new State().withState(State.Type.RUNNING))
                                                                .build()
                                                        )
                                                    )
                                                    .withState(State.Type.RUNNING);

                                            workerTaskResults.add(new WorkerTaskResult(updatedTaskRun));
                                        }
                                    }
                                } catch (Exception e) {
                                    workerTaskResults.add(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.FAILED)));
                                    workerTask.getRunContext().logger().error("Failed to evaluate the runIf condition for task {}. Cause: {}", workerTask.getTask().getId(), e.getMessage(), e);
                                }
                            }));

                        try {
                            executorService.addWorkerTaskResults(executor, workerTaskResults);
                        } catch (InternalException e) {
                            log.error("Unable to add a worker task result to the execution", e);
                        }
                    }

                    // subflow execution results
                    if (!executor.getSubflowExecutionResults().isEmpty()) {
                        executor.getSubflowExecutionResults()
                            .forEach(throwConsumer(subflowExecutionResult -> subflowExecutionResultQueue.emit(subflowExecutionResult)));
                    }

                    // schedulerDelay
                    if (!executor.getExecutionDelays().isEmpty()) {
                        executor.getExecutionDelays()
                            .forEach(executionDelay -> executionDelayStateStore.save(executionDelay));
                    }

                    // subflow executions
                    if (!executor.getSubflowExecutions().isEmpty()) {
                        executor.getSubflowExecutions().forEach(throwConsumer(subflowExecution -> {
                            Execution subExecution = subflowExecution.getExecution();
                            String msg = String.format("Created new execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]]", subExecution.getId(), subExecution.getFlowId(), subExecution.getNamespace());

                            log.info(msg);

                            logQueue.emit(LogEntry.of(subflowExecution.getParentTaskRun(), subflowExecution.getExecution().getKind()).toBuilder()
                                .level(Level.INFO)
                                .message(msg)
                                .timestamp(subflowExecution.getParentTaskRun().getState().getStartDate())
                                .thread(Thread.currentThread().getName())
                                .build()
                            );

                            executionQueue.emit(subflowExecution.getExecution());
                        }));
                    }

                    return executor;
                } catch (QueueException e) {
                    try {
                        Execution failedExecution = fail(execution, e);
                        this.executionQueue.emit(failedExecution);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", execution.getId(), ex);
                    }
                    Span.current().recordException(e).setStatus(StatusCode.ERROR);

                    return null;
                }
            }
        ));

        if (result != null) {
            this.toExecution(result);
        }
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage(), either.getRight());
            return;
        }

        WorkerTaskResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getTaskRun())) {
            log.warn(SKIPPING_EXECUTION, message.getTaskRun().getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        ExecutorContext executor = executionStateStore.lock(message.getTaskRun().getExecutionId(), execution -> {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getTaskRun())) {
                try {
                    // process worker task result
                    executorService.addWorkerTaskResult(current, throwSupplier(() -> findFlowOrThrow(execution)), message);
                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return handleFailedExecutionFromExecutor(current, e);
                }

                return Pair.of(
                    current,
                    pair.getRight()
                );
            }

            return null;
        });

        if (executor != null) {
            this.toExecution(executor);
        }
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

        if (skipExecutionService.skipExecution(killedExecution.getExecutionId())) {
            log.warn(SKIPPING_EXECUTION, killedExecution.getExecutionId());
            return;
        }

        metricRegistry
            .counter(MetricRegistry.METRIC_EXECUTOR_KILLED_COUNT, MetricRegistry.METRIC_EXECUTOR_KILLED_COUNT_DESCRIPTION, metricRegistry.tags(killedExecution))
            .increment();

        if (log.isDebugEnabled()) {
            executorService.log(log, true, killedExecution);
        }

        // Immediately fire the event in EXECUTED state to notify the Workers to kill
        // any remaining tasks for that executing regardless of if the execution exist or not.
        // Note, that this event will be a noop if all tasks for that execution are already killed or completed.
        try {
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .executionId(killedExecution.getExecutionId())
                .isOnKillCascade(false)
                .state(ExecutionKilled.State.EXECUTED)
                .tenantId(killedExecution.getTenantId())
                .build()
            );
        } catch (QueueException e) {
            log.error("Unable to kill the execution {}", killedExecution.getExecutionId(), e);
        }

        ExecutorContext executor = killingOrAfterKillState(killedExecution.getExecutionId(), Optional.ofNullable(killedExecution.getExecutionState()));

        // Check whether kill event should be propagated to downstream executions.
        // By default, always propagate the ExecutionKill to sub-flows (for backward compatibility).
        Boolean isOnKillCascade = Optional.ofNullable(killedExecution.getIsOnKillCascade()).orElse(true);
        if (isOnKillCascade) {
            executionService
                .killSubflowExecutions(event.getTenantId(), killedExecution.getExecutionId())
                .doOnNext(executionKilled -> {
                    try {
                        killQueue.emit(executionKilled);
                    } catch (QueueException e) {
                        log.error("Unable to kill the execution {}", executionKilled.getExecutionId(), e);
                    }
                })
                .blockLast();
        }

        if (executor != null) {
            // Transmit the new execution state. Note that the execution
            // will eventually transition to KILLED state before sub-flow executions are actually killed.
            // This behavior is acceptable due to the fire-and-forget nature of the killing event.
            this.toExecution(executor, true);
        }
    }

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution result: {}", either.getRight().getMessage());
            return;
        }

        SubflowExecutionResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getExecutionId())) {
            log.warn(SKIPPING_EXECUTION, message.getExecutionId());
            return;
        }
        if (skipExecutionService.skipExecution(message.getParentTaskRun())) {
            log.warn(SKIPPING_EXECUTION, message.getParentTaskRun().getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        ExecutorContext executor = executionStateStore.lock(message.getParentTaskRun().getExecutionId(), execution -> {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getParentTaskRun())) { // TODO if we remove this check, we can avoid adding 'iteration' on the 'isSame()' method
                try {
                    FlowWithSource flow = findFlowOrThrow(execution);
                    Task task = flow.findTaskByTaskId(message.getParentTaskRun().getTaskId());
                    TaskRun taskRun;

                    // iterative tasks
                    if (task instanceof ForEachItem.ForEachItemExecutable forEachItem) {
                        // For iterative tasks, we need to get the taskRun from the execution,
                        // move it to the state of the child flow, and merge the outputs.
                        // This is important to avoid races such as RUNNING that arrives after the first SUCCESS/FAILED.
                        RunContext runContext = runContextFactory.of(flow, task, current.getExecution(), message.getParentTaskRun());
                        taskRun = execution.findTaskRunByTaskRunId(message.getParentTaskRun().getId());
                        if (taskRun.getState().getCurrent() != message.getState()) {
                            taskRun = taskRun.withState(message.getState());
                        }
                        Map<String, Object> outputs = MapUtils.deepMerge(taskRun.getOutputs(), message.getParentTaskRun().getOutputs());
                        Variables variables = variablesService.of(StorageContext.forTask(taskRun), outputs);
                        taskRun = taskRun.withOutputs(variables);
                        taskRun = ExecutableUtils.manageIterations(
                            runContext.storage(),
                            taskRun,
                            current.getExecution(),
                            forEachItem.getTransmitFailed(),
                            forEachItem.isAllowFailure(),
                            forEachItem.isAllowWarning()
                        );
                    } else {
                        taskRun = message.getParentTaskRun();
                    }

                    Execution newExecution = current.getExecution().withTaskRun(taskRun);

                    // If the worker task result is killed, we must check if it has a parents to also kill them if not already done.
                    // Running flowable tasks that have child tasks running in the worker will be killed thanks to that.
                    if (taskRun.getState().getCurrent() == State.Type.KILLED && taskRun.getParentTaskRunId() != null) {
                        newExecution = executionService.killParentTaskruns(taskRun, newExecution);
                    }

                    current = current.withExecution(newExecution, "joinSubflowExecutionResult");

                    // send metrics on parent taskRun terminated
                    if (taskRun.getState().isTerminated()) {
                        metricRegistry
                            .counter(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(message))
                            .increment();

                        metricRegistry
                            .timer(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION, MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION_DESCRIPTION, metricRegistry.tags(message))
                            .record(taskRun.getState().getDurationOrComputeIt());

                        log.trace("TaskRun terminated: {}", taskRun);
                    }

                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return handleFailedExecutionFromExecutor(current, e);
                }

                return Pair.of(
                    current,
                    pair.getRight()
                );
            }

            return null;
        });

        if (executor != null) {
            this.toExecution(executor);
        }
    }

    private void subflowExecutionEndQueue(Either<SubflowExecutionEnd, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution end: {}", either.getRight().getMessage());
            return;
        }

        SubflowExecutionEnd message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getParentExecutionId())) {
            log.warn(SKIPPING_EXECUTION, message.getParentExecutionId());
            return;
        }
        if (skipExecutionService.skipExecution(message.getChildExecution())) {
            log.warn(SKIPPING_EXECUTION, message.getChildExecution().getId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        executionStateStore.lock(message.getParentExecutionId(), execution -> {
            if (execution == null) {
                throw new IllegalStateException("Execution state don't exist for " + message.getParentExecutionId() + ", receive " + message);
            }

            try {
                FlowWithSource flow = findFlowOrThrow(execution);
                ExecutableTask<?> executableTask = (ExecutableTask<?>) flow.findTaskByTaskId(message.getTaskId());
                if (!executableTask.waitForExecution()) {
                    return null;
                }

                TaskRun taskRun = execution.findTaskRunByTaskRunId(message.getTaskRunId()).withState(message.getState()).withOutputs(message.getOutputs());
                FlowInterface childFlow = flowMetaStore.findByExecution(message.getChildExecution()).orElseThrow();
                RunContext runContext = runContextFactory.of(
                    childFlow,
                    (Task) executableTask,
                    message.getChildExecution(),
                    taskRun
                );

                SubflowExecutionResult subflowExecutionResult = ExecutableUtils.subflowExecutionResultFromChildExecution(runContext, childFlow, message.getChildExecution(), executableTask, taskRun);
                if (subflowExecutionResult != null) {
                    try {
                        this.subflowExecutionResultQueue.emit(subflowExecutionResult);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the subflow execution result", ex);
                    }
                }
            } catch (InternalException | FlowNotFoundException e) {
                log.error("Unable to process the subflow execution end", e);
            }
            return null;
        });
    }

    private void multipleConditionEventQueue(Either<MultipleConditionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a multiple condition event: {}", either.getRight().getMessage());
            return;
        }

        MultipleConditionEvent multipleConditionEvent = either.getLeft();

        flowTriggerService.computeExecutionsFromFlowTriggers(multipleConditionEvent.execution(), List.of(multipleConditionEvent.flow()), Optional.of(multipleConditionStorage))
            .forEach(exec -> {
                try {
                    executionQueue.emit(exec);
                } catch (QueueException e) {
                    log.error("Unable to emit the execution {}", exec.getId(), e);
                }
            });
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

        executionDelayStateStore.processExpired(Instant.now(), executionDelay -> {
            ExecutorContext result = executionStateStore.lock(executionDelay.getExecutionId(), execution -> {
                ExecutorContext executor = new ExecutorContext(execution);

                metricRegistry
                    .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
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
                        FlowWithSource flow = findFlow(execution);
                        Execution newAttempt = executionService.retryTask(
                            execution,
                            flow,
                            executionDelay.getTaskRunId()
                        );
                        executor = executor.withExecution(newAttempt, "retryFailedTask");
                    }
                    // Handle failed flow retries
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)) {
                        Execution newExecution = executionService.replay(executor.getExecution(), null, null);
                        executor = executor.withExecution(newExecution, "retryFailedFlow");
                    }
                    // Handle WaitFor
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)) {
                        Execution newExecution  = executionService.retryWaitFor(executor.getExecution(), executionDelay.getTaskRunId());
                        executor = executor.withExecution(newExecution, "continueLoop");
                    }
                } catch (Exception e) {
                    executor = handleFailedExecutionFromExecutor(executor, e);
                }

                return executor;
            });

            if (result != null) {
                this.toExecution(result);
            }
        });
    }

    private void executionSLAMonitorLoop() {
        if (this.shutdown.get() || this.isPaused.get()) {
            return;
        }

        slaMonitorStateStore.processExpired(Instant.now(), slaMonitor -> {
            ExecutorContext result = executionStateStore.lock(slaMonitor.getExecutionId(), execution -> {
                FlowWithSource flow = findFlow(execution);
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
                            .counter(MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                            .increment();
                    }
                } catch (Exception e) {
                    executor = handleFailedExecutionFromExecutor(executor, e);
                }

                return executor;
            });

            if (result != null) {
                this.toExecution(result);
            }
        });
    }

    private void enterMaintenance() {
        this.executionQueue.pause();
        this.workerTaskResultQueue.pause();
        this.killQueue.pause();
        this.subflowExecutionResultQueue.pause();
        this.flowQueue.pause();

        this.isPaused.set(true);
        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.executionQueue.resume();
        this.workerTaskResultQueue.resume();
        this.killQueue.resume();
        this.subflowExecutionResultQueue.resume();
        this.flowQueue.resume();

        this.isPaused.set(false);
        this.setState(ServiceState.RUNNING);
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

    private ExecutorContext killingOrAfterKillState(final String executionId, Optional<State.Type> afterKillState) {
        return executionStateStore.lock(executionId, execution -> {
            FlowInterface flow = flowMetaStore.findByExecution(execution).orElseThrow();

            // remove it from the queued store if it was queued so it would not be restarted
            if (execution.getState().isQueued()) {
                executionQueuedStateStore.remove(execution);
            }

            Execution killing = executionService.kill(execution, flow, afterKillState);
            return new ExecutorContext(execution)
                .withExecution(killing, "joinKillingExecution");
        });
    }

    private void toExecution(ExecutorContext executor) {
        toExecution(executor, false);
    }

    private void toExecution(ExecutorContext executor, boolean ignoreFailure) {
        try {
            boolean shouldSend = false;

            if (executor.getException() != null) {
                executor = handleFailedExecutionFromExecutor(executor, executor.getException());
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
                    FlowWithSource flow = executor.getFlow();

                    if (flow == null) {
                        log.error("Couldn't reset trigger for execution {} as flow {} is missing. Trigger {} might stay stuck.",
                            execution.getId(),
                            execution.getTenantId() + "/" + execution.getNamespace() + "/" + execution.getFlowId(),
                            execution.getTrigger().getId()
                        );
                    } else {
                        triggerRepository
                            .findByExecution(execution)
                            .ifPresent(trigger -> this.triggerState.update(executionService.resetExecution(flow, execution, trigger)));
                    }
                }

                return;
            }

            if (log.isDebugEnabled()) {
                executorService.log(log, false, executor);
            }

            // the terminated state can come from the execution queue, in this case we always have a flow in the executor
            // or from a worker task in an afterExecution block, in this case we need to load the flow
            if (executor.getFlow() == null && executor.getExecution().getState().isTerminated()) {
                executor = executor.withFlow(findFlowOrThrow(executor.getExecution()));
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
                    Variables variables = variablesService.of(StorageContext.forExecution(executor.getExecution()), outputs);
                    SubflowExecutionEnd subflowExecutionEnd = new SubflowExecutionEnd(executor.getExecution(), parentExecutionId, taskRunId, taskId, execution.getState().getCurrent(), variables);
                    this.subflowExecutionEndQueue.emit(subflowExecutionEnd);
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
                    // as we may receive multiple time killed execution (one when we kill it, then one for each running worker task), we limit to the first we receive: when the state transitionned from KILLING to KILLED
                    boolean killingThenKilled = execution.getState().getCurrent().isKilled() && executor.getOriginalState() == State.Type.KILLING;
                    if (!queuedThenKilled && !concurrencyShortCircuitState && (!execution.getState().getCurrent().isKilled() || killingThenKilled)) {
                        int newLimit = concurrencyLimitStorage.decrement(executor.getFlow());

                        if (executor.getFlow().getConcurrency().getBehavior() == Concurrency.Behavior.QUEUE) {
                            var finalFlow = executor.getFlow();

                            if (newLimit < finalFlow.getConcurrency().getLimit()) {
                                executionQueuedStorage.pop(executor.getFlow().getTenantId(),
                                    executor.getFlow().getNamespace(),
                                    executor.getFlow().getId(),
                                    throwBiConsumer((dslContext, queued) -> {
                                        var newExecution = queued.withState(State.Type.RUNNING);
                                        concurrencyLimitStorage.increment(dslContext, finalFlow);
                                        executionQueue.emit(newExecution);
                                        metricRegistry.counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT_DESCRIPTION, metricRegistry.tags(newExecution)).increment();

                                        // process flow triggers to allow listening on RUNNING state after a QUEUED state
                                        processFlowTriggers(newExecution);
                                    })
                                );
                            } else {
                                log.error("Concurrency limit reached for flow {}.{} after decrementing the execution running count due to the terminated execution {}. No new executions will be dequeued.", executor.getFlow().getNamespace(), executor.getFlow().getId(), executor.getExecution().getId());
                            }
                        } else if (newLimit >= executor.getFlow().getConcurrency().getLimit()) {
                            log.error("Concurrency limit reached for flow {}.{} after decrementing the execution running count due to the terminated execution {}. This should not happen.", executor.getFlow().getNamespace(), executor.getFlow().getId(), executor.getExecution().getId());
                        }
                    }
                }

                // purge the trigger: reset scheduler trigger at end
                if (execution.getTrigger() != null) {
                    FlowWithSource flow = executor.getFlow();
                    triggerRepository
                        .findByExecution(execution)
                        .ifPresent(trigger -> {
                            this.triggerState.update(executionService.resetExecution(flow, execution, trigger));
                        });
                }

                if (cleanExecutionQueue) {
                    executionEventQueue.deleteByKey(executor.getExecution().getId());
                    executionQueue.deleteByKey(executor.getExecution().getId());
                }

                // Purge the workerTaskResultQueue and the workerJobQueue
                // IMPORTANT: this is safe as only the executor is listening to WorkerTaskResult,
                // and we are sure at this stage that all WorkerJob has been listened and processed by the Worker.
                // If any of these assumptions changed, this code would not be safe anymore.
                // One notable exception is for killed flow as the KILLED worker task result may arrive late so removing them is a racy as we may remove them before they are processed
                if (cleanWorkerJobQueue && !ListUtils.isEmpty(executor.getExecution().getTaskRunList()) && !execution.getState().getCurrent().isKilled()) {
                    List<String> taskRunKeys = executor.getExecution().getTaskRunList().stream()
                        .map(taskRun -> taskRun.getId())
                        .toList();
                    workerTaskResultQueue.deleteByKeys(taskRunKeys);
                    workerJobQueue.deleteByKeys(taskRunKeys);
                }

                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED);
                this.executionEventQueue.emit(event);
            } else {
                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED);
                this.executionEventQueue.emit(event);
            }
        } catch (QueueException | FlowNotFoundException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it
                executionStateStore.lock(executor.getExecution().getId(), execution -> {
                    try {
                        Execution failed = execution.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED);
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

    private void processFlowTriggers(Execution execution) throws QueueException {
        // directly process simple conditions
        flowTriggerService.withFlowTriggersOnly(allFlows.stream())
            .filter(f -> ListUtils.emptyOnNull(f.getTrigger().getConditions()).stream().noneMatch(c -> c instanceof MultipleCondition) && f.getTrigger().getPreconditions() == null)
            .map(f -> f.getFlow())
            .distinct() // as computeExecutionsFromFlowTriggers is based on flow, we must map FlowWithFlowTrigger to a flow and distinct to avoid multiple execution for the same flow
            .flatMap(f -> flowTriggerService.computeExecutionsFromFlowTriggerConditions(execution, f).stream())
            .forEach(throwConsumer(exec -> executionQueue.emit(exec)));

        // send multiple conditions to the multiple condition queue for later processing
        flowTriggerService.withFlowTriggersOnly(allFlows.stream())
            .filter(f -> ListUtils.emptyOnNull(f.getTrigger().getConditions()).stream().anyMatch(c -> c instanceof MultipleCondition) || f.getTrigger().getPreconditions() != null)
            .map(f -> new MultipleConditionEvent(f.getFlow(), execution))
            .distinct() // we can have multiple MultipleConditionEvent if a flow contains multiple triggers as it would lead to multiple FlowWithFlowTrigger
            .forEach(throwConsumer(multipleCondition -> multipleConditionEventQueue.emit(multipleCondition)));
    }

    private FlowWithSource findFlowOrThrow(Execution execution) throws FlowNotFoundException {
        return findFlow(execution).orElseThrow(() -> new FlowNotFoundException("Unable to find flow %s for execution %s".formatted(execution.getTenantId() + "/" + execution.getNamespace() + "/" + execution.getFlowId(), execution.getId())));
    }
    private FlowWithSource findFlow(Execution execution) {
        FlowInterface flow = flowMetaStore.findByExecution(execution).orElseThrow();
        return  pluginDefaultService.injectDefaults(flow, execution);
    }

    private ExecutorContext handleFailedExecutionFromExecutor(ExecutorContext executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);

        try {
            logQueue.emitAsync(failedExecutionWithLog.getLogs());
        } catch (QueueException ex) {
            // fail silently
        }

        return executor.withExecution(failedExecutionWithLog.getExecution(), "exception");
    }

    @Override
    @PreDestroy
    public void close() {
        if (shutdown.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("Terminating");
            }

            setState(ServiceState.TERMINATING);
            this.receiveCancellations.forEach(Runnable::run);
            ExecutorsUtils.closeScheduledThreadPool(scheduledExecutorService, Duration.ofSeconds(5), List.of(executionDelayFuture, monitorSLAFuture));
            setState(ServiceState.TERMINATED_GRACEFULLY);

            if (log.isDebugEnabled()) {
                log.debug("Closed ({})", state.get().name());
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ServiceType getType() {
        return ServiceType.EXECUTOR;
    }

    @Override
    public ServiceState getState() {
        return state.get();
    }

    private void setState(final ServiceState state) {
        this.state.set(state);
        eventPublisher.publishEvent(new ServiceStateChangeEvent(this));
    }
}
