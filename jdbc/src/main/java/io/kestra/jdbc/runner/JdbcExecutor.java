package io.kestra.jdbc.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.sla.*;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.*;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.*;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.*;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.plugin.core.flow.ForEachItem;
import io.kestra.plugin.core.flow.Template;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.Nullable;
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
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuppressWarnings("deprecation")
@Singleton
@JdbcRunnerEnabled
@Slf4j
public class JdbcExecutor implements ExecutorInterface, Service {
    private static final ObjectMapper MAPPER = JdbcMapper.of();

    private final ScheduledExecutorService scheduledDelay = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private AbstractJdbcExecutionRepository executionRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private QueueInterface<WorkerJob> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<FlowWithSource> flowQueue;

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
    private RunContextFactory runContextFactory;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private Optional<Template.TemplateExecutorInterface> templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ConditionService conditionService;

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
    private LogService logService;

    @Inject
    private SLAMonitorStorage slaMonitorStorage;

    @Inject
    private SLAService slaService;

    private final Tracer tracer;

    private final FlowRepositoryInterface flowRepository;

    private final JdbcServiceLivenessCoordinator serviceLivenessCoordinator;

    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    private final String id = IdUtils.create();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    private final List<Runnable> receiveCancellations = new ArrayList<>();

    /**
     * Creates a new {@link JdbcExecutor} instance. Both constructor and field injection are used
     * to force Micronaut to respect order when invoking pre-destroy order.
     *
     * @param serviceLivenessCoordinator The {@link JdbcServiceLivenessCoordinator}.
     * @param flowRepository             The {@link FlowRepositoryInterface}.
     * @param flowTopologyRepository     The {@link AbstractJdbcFlowTopologyRepository}.
     * @param eventPublisher             The {@link ApplicationEventPublisher}.
     */
    @Inject
    public JdbcExecutor(
        @Nullable final JdbcServiceLivenessCoordinator serviceLivenessCoordinator,
        final FlowRepositoryInterface flowRepository,
        final AbstractJdbcFlowTopologyRepository flowTopologyRepository,
        final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        final TracerFactory tracerFactory
        ) {
        this.serviceLivenessCoordinator = serviceLivenessCoordinator;
        this.flowRepository = flowRepository;
        this.flowTopologyRepository = flowTopologyRepository;
        this.eventPublisher = eventPublisher;
        this.tracer = tracerFactory.getTracer(JdbcExecutor.class, "EXECUTOR");
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

        this.receiveCancellations.addFirst(this.executionQueue.receive(Executor.class, this::executionQueue));
        this.receiveCancellations.addFirst(this.workerTaskResultQueue.receive(Executor.class, this::workerTaskResultQueue));
        this.receiveCancellations.addFirst(this.killQueue.receive(Executor.class, this::killQueue));
        this.receiveCancellations.addFirst(this.subflowExecutionResultQueue.receive(Executor.class, this::subflowExecutionResultQueue));
        this.receiveCancellations.addFirst(this.subflowExecutionEndQueue.receive(Executor.class, this::subflowExecutionEndQueue));
        this.clusterEventQueue.ifPresent(clusterEventQueueInterface -> this.receiveCancellations.addFirst(clusterEventQueueInterface.receive(this::clusterEventQueue)));

        ScheduledFuture<?> scheduledDelayFuture = scheduledDelay.scheduleAtFixedRate(
            this::executionDelaySend,
            0,
            1,
            TimeUnit.SECONDS
        );

        ScheduledFuture<?> scheduledSLAMonitorFuture = scheduledDelay.scheduleAtFixedRate(
            this::executionSLAMonitor,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exceptions on the scheduledDelay thread
        Thread.ofVirtual().name("jdbc-delay-exception-watcher").start(
            () -> {
                Await.until(scheduledDelayFuture::isDone);

                try {
                    scheduledDelayFuture.get();
                } catch (ExecutionException | InterruptedException | CancellationException e) {
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
                Await.until(scheduledSLAMonitorFuture::isDone);

                try {
                    scheduledSLAMonitorFuture.get();
                } catch (ExecutionException | InterruptedException | CancellationException e) {
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
                FlowWithSource flow;
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
                                    flow,
                                    this.allFlows.stream().filter(f -> Objects.equals(f.getTenantId(), flow.getTenantId())).toList()
                                )
                        )
                            .distinct()
                            .toList()
                    );
                } catch (Exception e) {
                    log.error("Unable to save flow topology", e);
                }

            }
        ));
        setState(ServiceState.RUNNING);
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
                this.workerTaskResultQueue.pause();
                this.killQueue.pause();
                this.subflowExecutionResultQueue.pause();
                this.flowQueue.pause();

                this.isPaused.set(true);
                this.setState(ServiceState.MAINTENANCE);
            }
            case MAINTENANCE_EXIT -> {
                this.executionQueue.resume();
                this.workerTaskResultQueue.resume();
                this.killQueue.resume();
                this.subflowExecutionResultQueue.resume();
                this.flowQueue.resume();

                this.isPaused.set(false);
                this.setState(ServiceState.RUNNING);
            }
        }
    }

    void reEmitWorkerJobsForWorkers(final Configuration configuration,
                                    final List<String> ids) {
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
                            workerTaskQueue.emit(WorkerTask.builder()
                                .taskRun(workerTaskRunning.getTaskRun().onRunningResend())
                                .task(workerTaskRunning.getTask())
                                .runContext(workerTaskRunning.getRunContext())
                                .build()
                            );
                            logService.logTaskRun(
                                workerTaskRunning.getTaskRun(),
                                log,
                                Level.WARN,
                                "Re-emitting WorkerTask."
                            );
                        } catch (QueueException e) {
                            logService.logTaskRun(
                                workerTaskRunning.getTaskRun(),
                                log,
                                Level.ERROR,
                                "Unable to re-emit WorkerTask.",
                                e
                            );
                        }
                    }
                }

                // WorkerTriggerRunning
                if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
                    try {
                        workerTaskQueue.emit(WorkerTrigger.builder()
                            .trigger(workerTriggerRunning.getTrigger())
                            .conditionContext(workerTriggerRunning.getConditionContext())
                            .triggerContext(workerTriggerRunning.getTriggerContext())
                            .build());
                        logService.logTrigger(
                            workerTriggerRunning.getTriggerContext(),
                            log,
                            Level.WARN,
                            "Re-emitting WorkerTrigger."
                        );
                    } catch (QueueException e) {
                        logService.logTrigger(
                            workerTriggerRunning.getTriggerContext(),
                            log,
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
                Flow.uidWithoutRevision(execution),
                () -> {
                    try {

                        final Flow flow = transform(this.flowRepository.findByExecutionWithSource(execution), execution);
                        Executor executor = new Executor(execution, null).withFlow(flow);

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
                        if (execution.getState().getCurrent() == State.Type.CREATED && !ListUtils.isEmpty(flow.getSla())) {
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

                        // queue execution if needed (limit concurrency)
                        if (execution.getState().getCurrent() == State.Type.CREATED && flow.getConcurrency() != null) {
                            ExecutionCount count = executionRepository.executionCounts(
                                flow.getTenantId(),
                                List.of(new io.kestra.core.models.executions.statistics.Flow(flow.getNamespace(), flow.getId())),
                                List.of(State.Type.RUNNING, State.Type.PAUSED),
                                null,
                                null,
                                null
                            ).getFirst();

                            executor = executorService.checkConcurrencyLimit(executor, flow, execution, count.getCount());

                            // the execution has been queued, we save the queued execution and stops here
                            if (executor.getExecutionRunning() != null && executor.getExecutionRunning().getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                executionQueuedStorage.save(ExecutionQueued.fromExecutionRunning(executor.getExecutionRunning()));
                                return Pair.of(
                                    executor,
                                    executorState
                                );
                            }

                            // the execution has been moved to FAILED or CANCELLED, we stop here
                            if (executor.getExecution().getState().isTerminated()) {
                                return Pair.of(
                                    executor,
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
                                executorService.onNexts(executor.getFlow(), executor.getExecution(), executor.getNexts()),
                                "onNexts"
                            );
                        }

                        // worker task
                        if (!executor.getWorkerTasks().isEmpty()) {
                            executor
                                .getWorkerTasks()
                                .stream()
                                .filter(workerTask -> this.deduplicateWorkerTask(execution, executorState, workerTask.getTaskRun()))
                                .forEach(throwConsumer(workerTask -> {
                                    try {
                                        if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                                            workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.SKIPPED)));
                                        } else {
                                            if (workerTask.getTask().isSendToWorkerTask()) {
                                                workerTaskQueue.emit(workerGroupService.resolveGroupFromJob(workerTask).map(group -> group.getKey()).orElse(null), workerTask);
                                            }
                                            if (workerTask.getTask().isFlowable()) {
                                                workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.RUNNING)));
                                            }
                                        }
                                    } catch (IllegalVariableEvaluationException e) {
                                        workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.FAILED)));
                                        workerTask.getRunContext().logger().error("Unable to evaluate the runIf condition for task {}", workerTask.getTask().getId(), e);
                                    }
                                }));


                        }

                        // worker tasks results
                        if (!executor.getWorkerTaskResults().isEmpty()) {
                            executor.getWorkerTaskResults()
                                .forEach(throwConsumer(workerTaskResult -> workerTaskResultQueue.emit(workerTaskResult)));
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

                                            logQueue.emit(LogEntry.of(subflowExecution.getParentTaskRun()).toBuilder()
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
                            this.executionQueue.emit(
                                message.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED)
                            );
                        } catch (QueueException ex) {
                            log.error("Unable to emit the execution {}", message.getId(), ex);
                        }
                        Span.current().recordException(e).setStatus(StatusCode.ERROR);

                        return null;
                    }
                }
            );
        });

        if (result != null) {
            this.toExecution(result);
        }
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage());
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
                    Flow flow = flowRepository.findByExecution(current.getExecution());

                    // dynamic tasks
                    Execution newExecution = executorService.addDynamicTaskRun(
                        current.getExecution(),
                        flow,
                        message
                    );
                    if (newExecution != null) {
                        current = current.withExecution(newExecution, "addDynamicTaskRun");
                    }

                    TaskRun taskRun = message.getTaskRun();
                    newExecution = current.getExecution().withTaskRun(taskRun);
                    // If the worker task result is killed, we must check if it has a parents to also kill them if not already done.
                    // Running flowable tasks that have child tasks running in the worker will be killed thanks to that.
                    if (taskRun.getState().getCurrent() == State.Type.KILLED && taskRun.getParentTaskRunId() != null) {
                        newExecution = executionService.killParentTaskruns(taskRun, newExecution);
                    }
                    current = current.withExecution(newExecution, "joinWorkerResult");

                    // send metrics on terminated
                    if (taskRun.getState().isTerminated()) {
                        metricRegistry
                            .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                            .increment();

                        metricRegistry
                            .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                            .record(taskRun.getState().getDuration());

                        log.trace("TaskRun terminated: {}", taskRun);
                        workerJobRunningRepository.deleteByKey(taskRun.getId());
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
                }
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
                    Flow flow = flowRepository.findByExecution(current.getExecution());
                    Task task = flow.findTaskByTaskId(message.getParentTaskRun().getTaskId());
                    TaskRun taskRun;

                    // iterative tasks
                    if (task instanceof ForEachItem.ForEachItemExecutable forEachItem) {
                        // For iterative tasks, we need to get the taskRun from the execution,
                        // move it to the state of the child flow, and merge the outputs.
                        // This is important to avoid races such as RUNNING that arrives after the first SUCCESS/FAILED.
                        RunContext runContext = runContextFactory.of(flow, task, current.getExecution(), message.getParentTaskRun());
                        taskRun = execution.findTaskRunByTaskRunId(message.getParentTaskRun().getId()).withState(message.getState());
                        Map<String, Object> outputs = MapUtils.merge(taskRun.getOutputs(), message.getParentTaskRun().getOutputs());
                        taskRun = taskRun.withOutputs(outputs);
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
                            .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                            .increment();

                        metricRegistry
                            .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                            .record(taskRun.getState().getDuration());

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
                }
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

            Flow flow = this.flowRepository.findByExecution(execution);
            try {
                ExecutableTask<?> executableTask = (ExecutableTask<?>) flow.findTaskByTaskId(message.getTaskId());
                if (!executableTask.waitForExecution()) {
                    return null;
                }

                TaskRun taskRun = execution.findTaskRunByTaskRunId(message.getTaskRunId()).withState(message.getState()).withOutputs(message.getOutputs());
                Flow childFlow = this.flowRepository.findByExecution(message.getChildExecution());
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
            } catch (InternalException e) {
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

        Executor executor = mayTransitExecutionToKillingStateAndGet(killedExecution.getExecutionId());

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

    private Executor mayTransitExecutionToKillingStateAndGet(final String executionId) {
        return executionRepository.lock(executionId, pair -> {
            Execution currentExecution = pair.getLeft();
            Flow flow = this.flowRepository.findByExecution(currentExecution);

            Execution killing = executionService.kill(currentExecution, flow);
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
                return;
            }

            if (log.isDebugEnabled()) {
                executorService.log(log, false, executor);
            }

            // emit for other consumer than executor if no failure
            if (hasFailure) {
                this.executionQueue.emit(executor.getExecution());
            } else {
                ((JdbcQueue<Execution>) this.executionQueue).emitOnly(null, executor.getExecution());
            }

            // delete if ended
            if (executorService.canBePurged(executor)) {
                executorStateStorage.delete(executor.getExecution());
            }

            Execution execution = executor.getExecution();
            // handle flow triggers on state change
            if (!execution.getState().getCurrent().equals(executor.getOriginalState())) {
                flowTriggerService.computeExecutionsFromFlowTriggers(execution, allFlows.stream().map(flow -> flow.toFlow()).toList(), Optional.of(multipleConditionStorage))
                    .forEach(throwConsumer(executionFromFlowTrigger -> this.executionQueue.emit(executionFromFlowTrigger)));
            }

            // handle actions on terminated state
            // the terminated state can only come from the execution queue, and in this case we always have a flow in the executor
            if (executor.getFlow() != null && conditionService.isTerminatedWithListeners(executor.getFlow(), executor.getExecution())) {
                // if there is a parent, we send a subflow execution result to it
                if (ExecutableUtils.isSubflow(execution)) {
                    // locate the parent execution to find the parent task run
                    String parentExecutionId = (String) execution.getTrigger().getVariables().get("executionId");
                    String taskRunId = (String) execution.getTrigger().getVariables().get("taskRunId");
                    String taskId = (String) execution.getTrigger().getVariables().get("taskId");
                    Map<String, Object> outputs = (Map<String, Object>) execution.getTrigger().getVariables().get("taskRunOutputs");
                    SubflowExecutionEnd subflowExecutionEnd = new SubflowExecutionEnd(executor.getExecution(), parentExecutionId, taskRunId, taskId, execution.getState().getCurrent(), outputs);
                    this.subflowExecutionEndQueue.emit(subflowExecutionEnd);
                }

                // purge SLA monitors
                if (!ListUtils.isEmpty(executor.getFlow().getSla()) && executor.getFlow().getSla().stream().anyMatch(ExecutionMonitoringSLA.class::isInstance)) {
                    slaMonitorStorage.purge(executor.getExecution().getId());
                }

                // check if there exist a queued execution and submit it to the execution queue
                if (executor.getFlow().getConcurrency() != null && executor.getFlow().getConcurrency().getBehavior() == Concurrency.Behavior.QUEUE) {
                    executionQueuedStorage.pop(executor.getFlow().getTenantId(),
                        executor.getFlow().getNamespace(),
                        executor.getFlow().getId(),
                        throwConsumer(queued -> executionQueue.emit(queued.withState(State.Type.RUNNING)))
                    );
                }
            }
        } catch (QueueException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it
                executionRepository.lock(executor.getExecution().getId(), pair -> {
                    Execution execution = pair.getLeft();
                    try {
                        this.executionQueue.emit(execution.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED));
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", execution.getId(), ex);
                    }
                    return null;
                });
            }
        }
    }

    private Flow transform(FlowWithSource flow, Execution execution) {
        if (templateExecutorInterface.isPresent()) {
            try {
                flow = Template.injectTemplate(
                    flow,
                    execution,
                    (tenantId, namespace, id) -> templateExecutorInterface.get().findById(tenantId, namespace, id).orElse(null)
                ).withSource(flow.getSource());
            } catch (InternalException e) {
                log.warn("Failed to inject template", e);
            }
        }

        return pluginDefaultService.injectDefaults(flow, execution);
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
                Flow flow = flowRepository.findByExecution(pair.getLeft());

                try {
                    // Handle paused tasks
                    if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW)) {
                        if (executionDelay.getTaskRunId() == null) {
                            // if taskRunId is null, this means we restart a flow that was delayed at startup (scheduled on)
                            Execution markAsExecution = pair.getKey().withState(executionDelay.getState());
                            executor = executor.withExecution(markAsExecution, "pausedRestart");
                        } else {
                            // if there is a taskRun it means we restart a paused task
                            Execution markAsExecution = executionService.markAs(
                                pair.getKey(),
                                flow,
                                executionDelay.getTaskRunId(),
                                executionDelay.getState()
                            );

                            executor = executor.withExecution(markAsExecution, "pausedRestart");
                        }
                    }
                    // Handle failed tasks
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)) {
                        Execution newAttempt = executionService.retryTask(
                            pair.getKey(),
                            executionDelay.getTaskRunId()
                        );
                        executor = executor.withExecution(newAttempt, "retryFailedTask");
                    }
                    // Handle failed flow
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)) {
                        Execution newExecution = executionService.replay(executor.getExecution(), null, null);
                        executor = executor.withExecution(newExecution, "retryFailedFlow");
                    }
                    else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)) {
                        Execution execution  = executionService.retryWaitFor(executor.getExecution(), executionDelay.getTaskRunId());
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
                Flow flow = flowRepository.findByExecution(pair.getLeft());
                Optional<SLA> sla = flow.getSla().stream().filter(s -> s.getId().equals(slaMonitor.getSlaId())).findFirst();
                if (sla.isEmpty()) {
                    // this can happen in case the flow has been updated and the SLA removed
                    log.debug("Cannot find the SLA '{}' if the flow for execution '{}', ignoring it.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                    return null;
                }

                try {
                    RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                    Optional<Violation> violation = slaService.evaluateExecutionMonitoringSLA(runContext, executor.getExecution(), sla.get());
                    if (violation.isPresent()) { // should always be true
                        log.info("Processing expired SLA monitor '{}' for execution '{}'.", slaMonitor.getSlaId(), slaMonitor.getExecutionId());
                        executor = executorService.processViolation(runContext, executor, violation.get());
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
                    log.trace("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
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
            log.trace("Duplicate WorkerTask on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
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
            log.trace("Duplicate SubflowExecution on execution '{}' for taskRun '{}', value '{}', taskId '{}', attempt '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId(), taskRun.getAttempts() == null ? null : taskRun.getAttempts().size() + 1);
            return false;
        } else {
            executorState.getSubflowExecutionDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private String deduplicationKey(TaskRun taskRun) {
        return taskRun.getId() + (taskRun.getAttempts() != null ? "-" + taskRun.getAttempts().size() : "") + (taskRun.getIteration() == null ? "" : "-" + taskRun.getIteration());
    }

    private Executor handleFailedExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);

        failedExecutionWithLog.getLogs().forEach(log -> {
            try {
                logQueue.emitAsync(log);
            } catch (QueueException ex) {
                // fail silently
            }
        });

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
            scheduledDelay.shutdown();
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