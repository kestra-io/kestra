package io.kestra.jdbc.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.executions.Execution.FailedExecutionWithLog;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.sla.*;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
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
import io.kestra.executor.ExecutorService;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.SLAService;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.plugin.core.flow.ForEachItem;
import io.kestra.plugin.core.flow.Template;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Configuration;
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

@SuppressWarnings("deprecation")
@Singleton
@JdbcRunnerEnabled
@Slf4j
public class JdbcExecutor implements ExecutorInterface {
    private static final ObjectMapper MAPPER = JdbcMapper.of();

    private final ScheduledExecutorService scheduledDelay = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> executionDelayFuture;
    private ScheduledFuture<?> monitorSLAFuture;

    @Inject
    private AbstractJdbcExecutionRepository executionRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

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
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<FlowInterface> flowQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;

    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONEND_NAMED)
    private QueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;

    @Inject
    @Named(QueueFactoryInterface.CLUSTER_EVENT_NAMED)
    private Optional<QueueInterface<ClusterEvent>> clusterEventQueue;

    @Inject
    @Named(QueueFactoryInterface.MULTIPLE_CONDITION_EVENT_NAMED)
    private QueueInterface<MultipleConditionEvent> multipleConditionEventQueue;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private Optional<Template.TemplateExecutorInterface> templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private MultipleConditionStorageInterface multipleConditionStorage;

    @Inject
    private FlowTriggerService flowTriggerService;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    protected FlowListenersInterface flowListeners;

    @Inject
    private ExecutionService executionService;

    @Inject
    private AbstractJdbcExecutionDelayStorage executionDelayStorage;

    @Inject
    private AbstractJdbcExecutionQueuedStorage executionQueuedStorage;

    @Inject
    private AbstractJdbcConcurrencyLimitStorage concurrencyLimitStorage;

    @Inject
    private AbstractJdbcExecutorStateStorage executorStateStorage;

    @Inject
    private FlowTopologyService flowTopologyService;

    protected List<FlowWithSource> allFlows;

    @Inject
    private WorkerGroupService workerGroupService;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private AbstractJdbcWorkerJobRunningRepository workerJobRunningRepository;

    @Inject
    private SLAMonitorStorage slaMonitorStorage;

    @Inject
    private SLAService slaService;

    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    private VariablesService variablesService;

    @Value("${kestra.jdbc.executor.clean.execution-queue:true}")
    private boolean cleanExecutionQueue;

    @Value("${kestra.jdbc.executor.clean.worker-queue:true}")
    private boolean cleanWorkerJobQueue;

    private final Tracer tracer;

    private final FlowMetaStoreInterface flowMetaStore;

    private final JdbcServiceLivenessCoordinator serviceLivenessCoordinator;

    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    private final MaintenanceService maintenanceService;

    private final String id = IdUtils.create();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    private final List<Runnable> receiveCancellations = new ArrayList<>();

    private final java.util.concurrent.ExecutorService workerTaskResultExecutorService;
    private final java.util.concurrent.ExecutorService executionExecutorService;
    private final int numberOfThreads;

    /**
     * Creates a new {@link JdbcExecutor} instance. Both constructor and field injection are used
     * to force Micronaut to respect order when invoking pre-destroy order.
     *
     * @param serviceLivenessCoordinator The {@link JdbcServiceLivenessCoordinator}.
     * @param flowMetaStore              The {@link FlowMetaStoreInterface}.
     * @param flowTopologyRepository     The {@link AbstractJdbcFlowTopologyRepository}.
     * @param eventPublisher             The {@link ApplicationEventPublisher}.
     */
    @Inject
    public JdbcExecutor(
        @Nullable final JdbcServiceLivenessCoordinator serviceLivenessCoordinator,
        final FlowMetaStoreInterface flowMetaStore,
        final AbstractJdbcFlowTopologyRepository flowTopologyRepository,
        final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        final TracerFactory tracerFactory,
        final ExecutorsUtils executorsUtils,
        final MaintenanceService maintenanceService,
        @Value("${kestra.jdbc.executor.thread-count:0}") final int threadCount
    ) {
        this.serviceLivenessCoordinator = serviceLivenessCoordinator;
        this.flowMetaStore = flowMetaStore;
        this.flowTopologyRepository = flowTopologyRepository;
        this.eventPublisher = eventPublisher;
        this.tracer = tracerFactory.getTracer(JdbcExecutor.class, "EXECUTOR");
        this.maintenanceService = maintenanceService;

        // By default, we start available processors count threads with a minimum of 4 by executor service
        // for the worker task result queue and the execution queue.
        // Other queues would not benefit from more consumers.
        this.numberOfThreads = threadCount != 0 ? threadCount : Math.max(4, Runtime.getRuntime().availableProcessors());
        this.workerTaskResultExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "jdbc-worker-task-result-executor");
        this.executionExecutorService = executorsUtils.maxCachedThreadPool(numberOfThreads, "jdbc-execution-executor");
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

    @SneakyThrows
    @Override
    public void run() {
        setState(ServiceState.CREATED);
        if (serviceLivenessCoordinator != null) {
            serviceLivenessCoordinator.setExecutor(this);
        }
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        Await.until(() -> this.allFlows != null, Duration.ofMillis(100), Duration.ofMinutes(5));

        this.receiveCancellations.addFirst(((JdbcQueue<Execution>) this.executionQueue).receiveBatch(
            Executor.class,
            executions -> {
                // process execution message grouped by executionId to avoid concurrency as the execution level as it would
                List<CompletableFuture<Void>> perExecutionFutures = executions.stream()
                    .filter(Either::isLeft)
                    .collect(Collectors.groupingBy(either -> either.getLeft().getId()))
                    .values()
                    .stream()
                    .map(eithers -> CompletableFuture.runAsync(() -> {
                        eithers.forEach(this::executionQueue);
                    }, executionExecutorService))
                    .toList();

                // directly process deserialization issues as most of the time there will be none
                executions.stream()
                    .filter(Either::isRight)
                    .forEach(either -> executionQueue(either));

                CompletableFuture.allOf(perExecutionFutures.toArray(CompletableFuture[]::new)).join();
            }
        ));
        this.receiveCancellations.addFirst(((JdbcQueue<WorkerTaskResult>) this.workerTaskResultQueue).receiveBatch(
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

        executionDelayFuture = scheduledDelay.scheduleAtFixedRate(
            this::executionDelaySend,
            0,
            1,
            TimeUnit.SECONDS
        );

        monitorSLAFuture = scheduledDelay.scheduleAtFixedRate(
            this::executionSLAMonitor,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exceptions on the scheduledDelay thread
        Thread.ofVirtual().name("jdbc-delay-exception-watcher").start(
            () -> {
                Await.until(executionDelayFuture::isDone);

                try {
                    executionDelayFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    if (e.getCause() != null && e.getCause().getClass() != CannotCreateTransactionException.class) {
                        log.error("Executor fatal exception in the scheduledDelay thread", e);
                        close();
                        KestraContext.getContext().shutdown();
                    }
                }
            }
        );

        // look at exceptions on the scheduledSLAMonitorFuture thread
        Thread.ofVirtual().name("jdbc-sla-monitor-exception-watcher").start(
            () -> {
                Await.until(monitorSLAFuture::isDone);

                try {
                    monitorSLAFuture.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    if (e.getCause() != null && e.getCause().getClass() != CannotCreateTransactionException.class) {
                        log.error("Executor fatal exception in the scheduledSLAMonitor thread", e);
                        close();
                        KestraContext.getContext().shutdown();
                    }
                }
            }
        );

        this.receiveCancellations.addFirst(flowQueue.receive(
            FlowTopology.class,
            either -> {
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
                    log.error("Unable to save flow topology for flow " + flow.uid(), e);
                }

            }
        ));

        if (this.maintenanceService.isInMaintenanceMode()) {
            enterMaintenance();
        } else {
            setState(ServiceState.RUNNING);
        }
        log.info("Executor started with {} thread(s)", numberOfThreads);
    }

    private void multipleConditionEventQueue(Either<MultipleConditionEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a multiple condition event: {}", either.getRight().getMessage());
            return;
        }

        MultipleConditionEvent multipleConditionEvent = either.getLeft();

        flowTriggerService.computeExecutionsFromFlowTriggerPreconditions(multipleConditionEvent.execution(), multipleConditionEvent.flow(), multipleConditionStorage)
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

    void reEmitWorkerJobsForWorkers(final Configuration configuration,
                                    final List<String> ids) {
        metricRegistry.counter(MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT, MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT_DESCRIPTION)
            .increment(ids.size());

        workerJobRunningRepository.getWorkerJobWithWorkerDead(configuration.dsl(), ids)
            .forEach(workerJobRunning -> {
                // WorkerTaskRunning
                if (workerJobRunning instanceof WorkerTaskRunning workerTaskRunning) {
                    if (skipExecutionService.skipExecution(workerTaskRunning.getTaskRun())) {
                        // if the execution is skipped, we remove the workerTaskRunning and skip its resubmission
                        log.warn("Skipping execution {}", workerTaskRunning.getTaskRun().getExecutionId());
                        workerJobRunningRepository.deleteByKey(workerTaskRunning.uid());
                    } else {
                        try {
                            workerJobQueue.emit(workerTaskRunning.getWorkerInstance().workerGroup(), WorkerTask.builder()
                                .taskRun(workerTaskRunning.getTaskRun().onRunningResend())
                                .task(workerTaskRunning.getTask())
                                .runContext(workerTaskRunning.getRunContext())
                                .build()
                            );
                            Logs.logTaskRun(
                                workerTaskRunning.getTaskRun(),
                                Level.WARN,
                                "Re-resubmitting WorkerTask."
                            );
                        } catch (QueueException e) {
                            Logs.logTaskRun(
                                workerTaskRunning.getTaskRun(),
                                Level.ERROR,
                                "Unable to re-resubmit WorkerTask.",
                                e
                            );
                        }
                    }
                }

                // WorkerTriggerRunning
                if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
                    try {
                        workerJobQueue.emit(workerTriggerRunning.getWorkerInstance().workerGroup(), WorkerTrigger.builder()
                            .trigger(workerTriggerRunning.getTrigger())
                            .conditionContext(workerTriggerRunning.getConditionContext())
                            .triggerContext(workerTriggerRunning.getTriggerContext())
                            .build());
                        Logs.logTrigger(
                            workerTriggerRunning.getTriggerContext(),
                            Level.WARN,
                            "Re-emitting WorkerTrigger."
                        );
                    } catch (QueueException e) {
                        Logs.logTrigger(
                            workerTriggerRunning.getTriggerContext(),
                            Level.ERROR,
                            "Unable to re-emit WorkerTrigger.",
                            e
                        );
                    }
                }
            });
    }

    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize an execution: {}", either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();
        if (skipExecutionService.skipExecution(message)) {
            log.warn("Skipping execution {}", message.getId());
            return;
        }

        Executor result = executionRepository.lock(message.getId(), pair -> {
            Execution execution = pair.getLeft();
            ExecutorState executorState = pair.getRight();

            return tracer.inCurrentContext(
                execution,
                FlowId.uidWithoutRevision(execution),
                () -> {
                    Executor executor = new Executor(execution, null);
                    try {
                        final FlowWithSource flow = findFlowOrThrow(execution);
                        executor = executor.withFlow(flow);

                        // schedule it for later if needed
                        if (execution.getState().getCurrent() == State.Type.CREATED && execution.getScheduleDate() != null && execution.getScheduleDate().isAfter(Instant.now())) {
                            ExecutionDelay executionDelay = ExecutionDelay.builder()
                                .executionId(executor.getExecution().getId())
                                .date(execution.getScheduleDate())
                                .state(State.Type.RUNNING)
                                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                                .build();
                            executionDelayStorage.save(executionDelay);
                            return Pair.of(
                                executor,
                                executorState
                            );
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
                            monitors.forEach(monitor -> slaMonitorStorage.save(monitor));
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

                            ExecutionRunning processed = concurrencyLimitStorage.countThenProcess(flow, (dslContext, concurrencyLimit) -> {
                                ExecutionRunning computed = executorService.processExecutionRunning(flow, concurrencyLimit.getRunning(), executionRunning.withExecution(execution)); // be sure that the execution running contains the latest value of the execution
                                if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.RUNNING && !computed.getExecution().getState().isTerminated()) {
                                    return Pair.of(computed, concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1));
                                } else if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                    executionQueuedStorage.save(dslContext, ExecutionQueued.fromExecutionRunning(computed));
                                }
                                return Pair.of(computed, concurrencyLimit);
                            });

                            // if the execution is queued or terminated due to concurrency limit, we stop here
                            if (processed.getExecution().getState().isTerminated() || processed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                return Pair.of(
                                    executor.withExecution(processed.getExecution(), "handleConcurrencyLimit"),
                                    executorState
                                );
                            }
                        }

                        // handle execution changed SLA
                        executor = executorService.handleExecutionChangedSLA(executor);

                        // process the execution
                        if (log.isDebugEnabled()) {
                            executorService.log(log, true, executor);
                        }
                        executor = executorService.process(executor);

                        if (!executor.getNexts().isEmpty() && deduplicateNexts(execution, executorState, executor.getNexts())) {
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
                                .stream()
                                .filter(workerTask -> this.deduplicateWorkerTask(execution, executorState, workerTask.getTaskRun()))
                                .forEach(throwConsumer(workerTask -> {
                                    try {
                                        if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                                            workerTaskResults.add(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.SKIPPED).addAttempt(TaskRunAttempt.builder().state(new State().withState(State.Type.SKIPPED)).build())));
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
                                .forEach(executionDelay -> executionDelayStorage.save(executionDelay));
                        }

                        // subflow executions
                        if (!executor.getSubflowExecutions().isEmpty()) {
                            List<SubflowExecution<?>> subflowExecutionDedup = executor
                                .getSubflowExecutions()
                                .stream()
                                .filter(subflowExecution -> this.deduplicateSubflowExecution(execution, executorState, subflowExecution.getParentTaskRun()))
                                .toList();

                            subflowExecutionDedup
                                .forEach(throwConsumer(subflowExecution -> {
                                    Execution subExecution = subflowExecution.getExecution();
                                    String log = String.format("Created new execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]]", subExecution.getId(), subExecution.getFlowId(), subExecution.getNamespace());

                                    JdbcExecutor.log.info(log);

                                    logQueue.emit(LogEntry.of(subflowExecution.getParentTaskRun(), subflowExecution.getExecution().getKind()).toBuilder()
                                        .level(Level.INFO)
                                        .message(log)
                                        .timestamp(subflowExecution.getParentTaskRun().getState().getStartDate())
                                        .thread(Thread.currentThread().getName())
                                        .build()
                                    );

                                    executionQueue.emit(subflowExecution.getExecution());
                                }));
                        }

                        return Pair.of(
                            executor,
                            executorState
                        );
                    } catch (QueueException e) {
                        try {
                            Execution failedExecution = fail(message, e);
                            this.executionQueue.emit(failedExecution);
                        } catch (QueueException ex) {
                            log.error("Unable to emit the execution {}", message.getId(), ex);
                        }
                        Span.current().recordException(e).setStatus(StatusCode.ERROR);

                        return null;
                    } catch (FlowNotFoundException e) {
                        // avoid infinite loop
                        if (!executor.getExecution().getState().getCurrent().isFailed()) {
                            return Pair.of(
                                failExecutionFromExecutor(executor, e),
                                executorState
                            );
                        }
                    }

                    return Pair.of(
                        executor,
                        executorState
                    );
                }
            );
        });

        if (result != null) {
            this.toExecution(result);
        }
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        try {
            logQueue.emitAsync(failedExecution.getLogs());
        } catch (QueueException ex) {
            // fail silently
        }
        return failedExecution.getExecution().getState().isFailed() ? failedExecution.getExecution() : failedExecution.getExecution().withState(State.Type.FAILED);
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage(), either.getRight());
            return;
        }

        WorkerTaskResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getTaskRun())) {
            log.warn("Skipping execution {}", message.getTaskRun().getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        Executor executor = executionRepository.lock(message.getTaskRun().getExecutionId(), pair -> {
            Execution execution = pair.getLeft();
            Executor current = new Executor(execution, null);

            if (execution == null) {
                throw new IllegalStateException("Execution state don't exist for " + message.getTaskRun().getExecutionId() + ", receive " + message);
            }

            if (execution.hasTaskRunJoinable(message.getTaskRun())) {
                try {
                    // process worker task result
                    executorService.addWorkerTaskResult(current, throwSupplier(() -> findFlowOrThrow(execution)), message);
                    // join worker result
                    return Pair.of(
                        current,
                        pair.getRight()
                    );
                } catch (InternalException e) {
                    return Pair.of(
                        handleFailedExecutionFromExecutor(current, e),
                        pair.getRight()
                    );
                } catch (FlowNotFoundException e) {
                    // avoid infinite loop
                    if (!current.getExecution().getState().getCurrent().isFailed()) {
                        return Pair.of(
                            handleFailedExecutionFromExecutor(current, e),
                            pair.getRight()
                        );
                    }
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

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a subflow execution result: {}", either.getRight().getMessage());
            return;
        }

        SubflowExecutionResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getExecutionId())) {
            log.warn("Skipping execution {}", message.getExecutionId());
            return;
        }
        if (skipExecutionService.skipExecution(message.getParentTaskRun())) {
            log.warn("Skipping execution {}", message.getParentTaskRun().getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        Executor executor = executionRepository.lock(message.getParentTaskRun().getExecutionId(), pair -> {
            Execution execution = pair.getLeft();
            Executor current = new Executor(execution, null);

            if (execution == null) {
                throw new IllegalStateException("Execution state don't exist for " + message.getParentTaskRun().getExecutionId() + ", receive " + message);
            }

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
                    return Pair.of(
                        current,
                        pair.getRight()
                    );
                } catch (InternalException e) {
                    return Pair.of(
                        handleFailedExecutionFromExecutor(current, e),
                        pair.getRight()
                    );
                } catch (FlowNotFoundException e) {
                    // avoid infinite loop
                    if (!current.getExecution().getState().getCurrent().isFailed()) {
                        return Pair.of(
                            handleFailedExecutionFromExecutor(current, e),
                            pair.getRight()
                        );
                    }
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
            log.warn("Skipping execution {}", message.getParentExecutionId());
            return;
        }
        if (skipExecutionService.skipExecution(message.getChildExecution())) {
            log.warn("Skipping execution {}", message.getChildExecution().getId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        executionRepository.lock(message.getParentExecutionId(), pair -> {
            Execution execution = pair.getLeft();

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
            log.warn("Skipping execution {}", killedExecution.getExecutionId());
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

        Executor executor = killingOrAfterKillState(killedExecution.getExecutionId(), Optional.ofNullable(killedExecution.getExecutionState()));

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

    private Executor killingOrAfterKillState(final String executionId, Optional<State.Type> afterKillState) {
        return executionRepository.lock(executionId, pair -> {
            Execution currentExecution = pair.getLeft();
            FlowInterface flow = flowMetaStore.findByExecution(currentExecution).orElseThrow();

            // remove it from the queued store if it was queued so it would not be restarted
            if (currentExecution.getState().isQueued()) {
                executionQueuedStorage.remove(currentExecution);
            }

            Execution killing = executionService.kill(currentExecution, flow, afterKillState);
            Executor current = new Executor(currentExecution, null)
                .withExecution(killing, "joinKillingExecution");
            return Pair.of(current, pair.getRight());
        });
    }

    private void toExecution(Executor executor) {
        toExecution(executor, false);
    }

    private void toExecution(Executor executor, boolean ignoreFailure) {
        try {
            boolean shouldSend = false;
            boolean hasFailure = false;

            if (executor.getException() != null) {
                executor = handleFailedExecutionFromExecutor(executor, executor.getException());
                shouldSend = true;
                hasFailure = true;
            } else if (executor.isExecutionUpdated()) {
                shouldSend = true;
            }

            if (!shouldSend) {
                Execution execution = executor.getExecution();

                // delete the execution from the state storage if ended
                // IMPORTANT: it must be done here as it's when the execution arrives 'again' with a terminated state,
                // so we are sure at this point that no new executions will be created otherwise the tate storage would be re-created by the execution queue.
                if (executorService.canBePurged(executor)) {
                    executorStateStorage.delete(execution);
                }

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
                        triggerRepository.findByUid(Trigger.uid(execution)).ifPresent(trigger -> this.triggerState.update(executionService.resetExecution(flow, execution, trigger)));
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

            // purge the executionQueue
            // IMPORTANT: this must be done before emitting the last execution message so that all consumers are notified that the execution ends.
            // NOTE: we may also purge ExecutionKilled events, but as there may not be a lot of them, it may not be worth it.
            if (cleanExecutionQueue && isTerminated) {
                ((JdbcQueue<Execution>) executionQueue).deleteByKey(executor.getExecution().getId());
            }

            // emit for other consumers than the executor if no failure
            if (hasFailure) {
                this.executionQueue.emit(executor.getExecution());
            } else {
                ((JdbcQueue<Execution>) this.executionQueue).emitOnly(null, executor.getExecution());
            }

            Execution execution = executor.getExecution();
            // handle flow triggers on state change
            if (!execution.getState().getCurrent().equals(executor.getOriginalState())) {
                processFlowTriggers(execution);
            }

            // handle actions on terminated state
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
                    slaMonitorStorage.purge(executor.getExecution().getId());
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
                    triggerRepository.findByUid(Trigger.uid(execution)).ifPresent(trigger -> this.triggerState.update(executionService.resetExecution(flow, execution, trigger)));
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
                    ((JdbcQueue<WorkerTaskResult>) workerTaskResultQueue).deleteByKeys(taskRunKeys);
                    ((JdbcQueue<WorkerJob>) workerJobQueue).deleteByKeys(taskRunKeys);
                }
            }
        } catch (QueueException | FlowNotFoundException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it
                executionRepository.lock(executor.getExecution().getId(), pair -> {
                    Execution execution = pair.getLeft();
                    Execution failedExecution = fail(execution, e);
                    try {
                        this.executionQueue.emit(failedExecution);
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

    private Optional<FlowWithSource> findFlow(Execution execution) {
        Optional<FlowInterface> maybeFlow = this.flowMetaStore.findByExecution(execution);
        if (maybeFlow.isEmpty()) {
            return Optional.empty();
        }

        FlowInterface flow = maybeFlow.get();
        FlowWithSource flowWithSource = pluginDefaultService.injectDefaults(flow, execution);

        if (templateExecutorInterface.isPresent()) {
            try {
                flowWithSource = Template.injectTemplate(
                    flowWithSource,
                    execution,
                    (tenantId, namespace, id) -> templateExecutorInterface.get().findById(tenantId, namespace, id).orElse(null)
                );
            } catch (InternalException e) {
                log.warn("Failed to inject template", e);
            }
        }

        return Optional.of(flowWithSource);
    }

    /**
     * ExecutionDelay is currently two types of execution:
     * <br/>
     * - Paused flow that will be restarted after an interval/timeout
     * <br/>
     * - Failed flow that will be retried after an interval
     **/
    private void executionDelaySend() {
        if (this.shutdown.get() || this.isPaused.get()) {
            return;
        }

        executionDelayStorage.get(executionDelay -> {
            Executor result = executionRepository.lock(executionDelay.getExecutionId(), pair -> {
                Executor executor = new Executor(pair.getLeft(), null);

                metricRegistry
                    .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                    .increment();

                try {
                    // Handle paused tasks and scheduledAt
                    if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW) && !pair.getLeft().getState().isTerminated()) {
                        if (executionDelay.getTaskRunId() == null) {
                            // if taskRunId is null, this means we restart a flow that was delayed at startup (scheduled on)
                            Execution markAsExecution = pair.getKey().withState(executionDelay.getState());
                            executor = executor.withExecution(markAsExecution, "pausedRestart");
                        } else {
                            // if there is a taskRun it means we restart a paused task
                            FlowInterface flow = flowMetaStore.findByExecution(pair.getLeft()).orElseThrow();
                            Execution markAsExecution = executionService.markAs(
                                pair.getKey(),
                                flow,
                                executionDelay.getTaskRunId(),
                                executionDelay.getState()
                            );

                            executor = executor.withExecution(markAsExecution, "pausedRestart");
                        }
                    }
                    // Handle failed task retries
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)) {
                        Execution newAttempt = executionService.retryTask(
                            pair.getKey(),
                            findFlowOrThrow(pair.getKey()),
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
                        Execution execution = executionService.retryWaitFor(executor.getExecution(), executionDelay.getTaskRunId());
                        executor = executor.withExecution(execution, "continueLoop");
                    }
                } catch (Exception e) {
                    executor = handleFailedExecutionFromExecutor(executor, e);
                }

                return Pair.of(
                    executor,
                    pair.getRight()
                );
            });

            if (result != null) {
                this.toExecution(result);
            }
        });
    }

    private void executionSLAMonitor() {
        if (this.shutdown.get() || this.isPaused.get()) {
            return;
        }

        slaMonitorStorage.processExpired(Instant.now(), slaMonitor -> {
            Executor result = executionRepository.lock(slaMonitor.getExecutionId(), pair -> {
                Executor executor = new Executor(pair.getLeft(), null);
                try {
                    // TODO flow with source is not needed here. Maybe the best would be to not add the flow inside the executor to trace all usage
                    FlowWithSource flow = findFlowOrThrow(pair.getLeft());
                    executor = executor.withFlow(flow);
                    Optional<SLA> sla = flow.getSla().stream().filter(s -> s.getId().equals(slaMonitor.getSlaId())).findFirst();
                    if (sla.isEmpty()) {
                        // this can happen in case the flow has been updated and the SLA removed
                        log.debug("Cannot find the SLA '{}' in the flow for execution '{}', ignoring it.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                        return null;
                    }

                    metricRegistry
                        .counter(MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_EXPIRED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                        .increment();

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

                return Pair.of(
                    executor,
                    pair.getRight()
                );
            });

            if (result != null) {
                this.toExecution(result);
            }
        });
    }

    private boolean deduplicateNexts(Execution execution, ExecutorState executorState, List<TaskRun> taskRuns) {
        return taskRuns
            .stream()
            .anyMatch(taskRun -> {
                // As retry is now handled outside the worker,
                // we now add the attempt size to the deduplication key
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" +
                    taskRun.getTaskId() + "-" +
                    taskRun.getValue() + "-" +
                    (taskRun.getAttempts() != null ? taskRun.getAttempts().size() : 0)
                    + taskRun.getIteration();

                if (executorState.getChildDeduplication().containsKey(deduplicationKey)) {
                    log.warn("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
                    return false;
                } else {
                    executorState.getChildDeduplication().put(deduplicationKey, taskRun.getId());
                    return true;
                }
            });
    }

    private boolean deduplicateWorkerTask(Execution execution, ExecutorState executorState, TaskRun taskRun) {
        String deduplicationKey = taskRun.getId() +
            (taskRun.getAttempts() != null ? taskRun.getAttempts().size() : 0)
            + taskRun.getIteration();
        State.Type current = executorState.getWorkerTaskDeduplication().get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.warn("Duplicate WorkerTask on execution '{}' for taskRun '{}', value '{}', taskId '{}' on state {}", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId(), current);
            return false;
        } else {
            executorState.getWorkerTaskDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private boolean deduplicateSubflowExecution(Execution execution, ExecutorState executorState, TaskRun taskRun) {
        // There can be multiple executions for the same task, so we need to deduplicated with the worker task execution iteration
        String deduplicationKey = deduplicationKey(taskRun);
        State.Type current = executorState.getSubflowExecutionDeduplication().get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.warn("Duplicate SubflowExecution on execution '{}' for taskRun '{}', value '{}', taskId '{}', attempt '{}' on state {}", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId(), taskRun.getAttempts() == null ? null : taskRun.getAttempts().size() + 1, current);
            return false;
        } else {
            executorState.getSubflowExecutionDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private String deduplicationKey(TaskRun taskRun) {
        return taskRun.getId() + (taskRun.getAttempts() != null ? "-" + taskRun.getAttempts().size() : "") + (taskRun.getIteration() == null ? "" : "-" + taskRun.getIteration());
    }

    private Executor failExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog;
        if (!executor.getExecution().hasFailed()) {
            failedExecutionWithLog = executor.getExecution().withState(State.Type.FAILED).failedExecutionFromExecutor(e);
        } else {
            failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);
        }

        return handleFailedExecutionFromExecutor(executor, failedExecutionWithLog);
    }

    private Executor handleFailedExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);

        return handleFailedExecutionFromExecutor(executor, failedExecutionWithLog);
    }

    private Executor handleFailedExecutionFromExecutor(Executor executor, FailedExecutionWithLog failedExecutionWithLog) {
        try {
            logQueue.emitAsync(failedExecutionWithLog.getLogs());
        } catch (QueueException ex) {
            // fail silently
        }

        return executor.withExecution(failedExecutionWithLog.getExecution(), "exception");
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    @PreDestroy
    public void close() {
        if (shutdown.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("Terminating");
            }

            setState(ServiceState.TERMINATING);
            this.receiveCancellations.forEach(Runnable::run);
            ExecutorsUtils.closeScheduledThreadPool(scheduledDelay, Duration.ofSeconds(5), List.of(executionDelayFuture, monitorSLAFuture));
            setState(ServiceState.TERMINATED_GRACEFULLY);

            if (log.isDebugEnabled()) {
                log.debug("Closed ({})", state.get().name());
            }
        }
    }

    private void setState(final ServiceState state) {
        this.state.set(state);
        eventPublisher.publishEvent(new ServiceStateChangeEvent(this));
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
        return ServiceType.EXECUTOR;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceState getState() {
        return state.get();
    }
}
