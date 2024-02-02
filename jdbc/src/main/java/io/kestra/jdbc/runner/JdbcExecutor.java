package io.kestra.jdbc.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultFlowExecutor;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.core.tasks.flows.ForEachItem;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@JdbcRunnerEnabled
@Slf4j
public class JdbcExecutor implements ExecutorInterface {
    private static final ObjectMapper MAPPER = JdbcMapper.of();

    private final ScheduledExecutorService scheduledDelay = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService scheduledHeartbeat = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean isShutdown = false;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

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
    private RunContextFactory runContextFactory;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private Optional<Template.TemplateExecutorInterface> templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ConditionService conditionService;

    @Inject
    private MultipleConditionStorageInterface multipleConditionStorage;

    @Inject
    private AbstractFlowTriggerService flowTriggerService;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    protected FlowListenersInterface flowListeners;

    // TODO we may be able to remove this storage and check that we have a parent execution or a dedicated trigger class and send a subflow execution result if needed
    @Inject
    private AbstractJdbcSubflowExecutionStorage subflowExecutionStorage;

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

    @Inject
    private AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    @Inject
    private AbstractJdbcWorkerInstanceRepository workerInstanceRepository;

    protected List<Flow> allFlows;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    @Inject
    private WorkerGroupService workerGroupService;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private AbstractJdbcWorkerJobRunningRepository workerJobRunningRepository;

    @Value("${kestra.heartbeat.frequency}")
    private Duration frequency;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;

    @SneakyThrows
    @Override
    public void run() {
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        Await.until(() -> this.allFlows != null, Duration.ofMillis(100), Duration.ofMinutes(5));

        applicationContext.registerSingleton(new DefaultFlowExecutor(flowListeners, this.flowRepository));

        this.executionQueue.receive(Executor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(Executor.class, this::workerTaskResultQueue);
        this.killQueue.receive(Executor.class, this::killQueue);
        this.subflowExecutionResultQueue.receive(Executor.class, this::subflowExecutionResultQueue);

        ScheduledFuture<?> scheduledDelayFuture = scheduledDelay.scheduleAtFixedRate(
            this::executionDelaySend,
            0,
            1,
            TimeUnit.SECONDS
        );

        ScheduledFuture<?> scheduledHeartbeatFuture = scheduledHeartbeat.scheduleAtFixedRate(
            this::workersUpdate,
            frequency.toSeconds(),
            frequency.toSeconds(),
            TimeUnit.SECONDS
        );

        // look at exceptions on the scheduledDelay thread
        Thread scheduledDelayExceptionThread = new Thread(
            () -> {
                Await.until(scheduledDelayFuture::isDone);

                try {
                    scheduledDelayFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    if (e.getCause().getClass() != CannotCreateTransactionException.class) {
                        log.error("Executor fatal exception in the scheduledDelay thread", e);

                        try {
                            close();
                            applicationContext.stop();
                        } catch (IOException ioe) {
                            log.error("Unable to properly close the executor", ioe);
                        }
                    }
                }
            },
            "jdbc-delay-exception-watcher"
        );
        scheduledDelayExceptionThread.start();

        // look at exceptions on the scheduledHeartbeat thread
        Thread scheduledHeartbeatExceptionThread = new Thread(
            () -> {
                Await.until(scheduledHeartbeatFuture::isDone);

                try {
                    scheduledHeartbeatFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    if (e.getCause().getClass() != CannotCreateTransactionException.class) {
                        log.error("Executor fatal exception in the scheduledHeartbeat thread", e);

                        try {
                            close();
                            applicationContext.stop();
                        } catch (IOException ioe) {
                            log.error("Unable to properly close the executor", ioe);
                        }
                    }
                }
            },
            "jdbc-heartbeat-exception-watcher"
        );
        scheduledHeartbeatExceptionThread.start();

        flowQueue.receive(
            FlowTopology.class,
            either -> {
                Flow flow;
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

                flowTopologyRepository.save(
                    flow,
                    (flow.isDeleted() ?
                        Stream.<FlowTopology>empty() :
                        flowTopologyService
                            .topology(
                                flow,
                                this.allFlows.stream()
                            )
                    )
                        .distinct()
                        .collect(Collectors.toList())
                );
            }
        );

    }

    protected void workersUpdate() {
        workerInstanceRepository.lockedWorkersUpdate(context -> {
            List<WorkerInstance> workersToDelete = workerInstanceRepository
                .findAllToDelete(context);
            List<String> workersToDeleteUuids = workersToDelete.stream().map(worker -> worker.getWorkerUuid().toString()).collect(Collectors.toList());

            // Before deleting a worker, we resubmit all his tasks
            workerJobRunningRepository.getWorkerJobWithWorkerDead(context, workersToDeleteUuids)
                .forEach(workerJobRunning -> {
                    if (workerJobRunning instanceof WorkerTaskRunning workerTaskRunning) {
                        if (skipExecutionService.skipExecution(workerTaskRunning.getTaskRun().getExecutionId())) {
                            // if the execution is skipped, we remove the workerTaskRunning and skip its resubmission
                            log.warn("Skipping execution {}", workerTaskRunning.getTaskRun().getId());
                            workerJobRunningRepository.deleteByKey(workerTaskRunning.uid());
                        } else {
                            workerTaskQueue.emit(WorkerTask.builder()
                                .taskRun(workerTaskRunning.getTaskRun())
                                .task(workerTaskRunning.getTask())
                                .runContext(workerTaskRunning.getRunContext())
                                .build()
                            );

                            log.warn(
                                "[namespace: {}] [flow: {}] [execution: {}] [taskrun: {}] WorkerTask is being resend",
                                workerTaskRunning.getTaskRun().getNamespace(),
                                workerTaskRunning.getTaskRun().getFlowId(),
                                workerTaskRunning.getTaskRun().getExecutionId(),
                                workerTaskRunning.getTaskRun().getId()
                            );
                        }

                    } else if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
                        workerTaskQueue.emit(WorkerTrigger.builder()
                            .trigger(workerTriggerRunning.getTrigger())
                            .conditionContext(workerTriggerRunning.getConditionContext())
                            .triggerContext(workerTriggerRunning.getTriggerContext())
                            .build());

                        log.warn(
                            "[namespace: {}] [flow: {}] [trigger: {}] WorkerTrigger is being resend",
                            workerTriggerRunning.getTriggerContext().getNamespace(),
                            workerTriggerRunning.getTriggerContext().getFlowId(),
                            workerTriggerRunning.getTriggerContext().getTriggerId()
                        );
                    } else {
                        throw new IllegalArgumentException("Object is of type " + workerJobRunning.getClass() + " which should never occurs");
                    }
                });

            workersToDelete.forEach(worker -> {
                workerInstanceRepository.delete(context, worker);
            });

            return null;
        });
    }

    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize an execution: {}", either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getId())) {
            log.warn("Skipping execution {}", message.getId());
            return;
        }

        Executor result = executionRepository.lock(message.getId(), pair -> {
            Execution execution = pair.getLeft();
            ExecutorState executorState = pair.getRight();

            final Flow flow = transform(this.flowRepository.findByExecution(execution), execution);
            Executor executor = new Executor(execution, null).withFlow(flow);

            // queue execution if needed (limit concurrency)
            if (execution.getState().getCurrent() == State.Type.CREATED && flow.getConcurrency() != null) {
                ExecutionCount count = executionRepository.executionCounts(
                    flow.getTenantId(),
                    List.of(new io.kestra.core.models.executions.statistics.Flow(flow.getNamespace(), flow.getId())),
                    List.of(State.Type.RUNNING, State.Type.PAUSED),
                    null,
                    null
                ).get(0);

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
                List<WorkerTask> workerTasksDedup = executor
                    .getWorkerTasks()
                    .stream()
                    .filter(workerTask -> this.deduplicateWorkerTask(execution, executorState, workerTask.getTaskRun()))
                    .toList();

                // WorkerTask not flowable to workerTask
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isSendToWorkerTask())
                    .forEach(workerTask -> workerTaskQueue.emit(workerGroupService.resolveGroupFromJob(workerTask), workerTask));

                // WorkerTask flowable to workerTaskResult as Running
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isFlowable())
                    .map(workerTask -> new WorkerTaskResult(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING))))
                    .forEach(workerTaskResultQueue::emit);
            }

            // worker tasks results
            if (!executor.getWorkerTaskResults().isEmpty()) {
                executor.getWorkerTaskResults()
                    .forEach(workerTaskResultQueue::emit);
            }

            // subflow execution results
            if (!executor.getSubflowExecutionResults().isEmpty()) {
                executor.getSubflowExecutionResults()
                    .forEach(subflowExecutionResultQueue::emit);
            }

            // schedulerDelay
            if (!executor.getExecutionDelays().isEmpty()) {
                executor.getExecutionDelays()
                    .forEach(executionDelay -> executionDelayStorage.save(executionDelay));
            }

            // subflow execution watchers
            if (!executor.getSubflowExecutions().isEmpty()) {
                subflowExecutionStorage.save(executor.getSubflowExecutions());

                List<SubflowExecution<?>> subflowExecutionDedup = executor
                    .getSubflowExecutions()
                    .stream()
                    .filter(subflowExecution -> this.deduplicateSubflowExecution(execution, executorState, subflowExecution.getParentTaskRun()))
                    .toList();

                subflowExecutionDedup
                    .forEach(subflowExecution -> {
                        String log = "Create new execution for flow '" +
                            subflowExecution.getExecution()
                                .getNamespace() + "'.'" + subflowExecution.getExecution().getFlowId() +
                            "' with id '" + subflowExecution.getExecution().getId() + "'";

                        JdbcExecutor.log.info(log);

                        logQueue.emit(LogEntry.of(subflowExecution.getParentTaskRun()).toBuilder()
                            .level(Level.INFO)
                            .message(log)
                            .timestamp(subflowExecution.getParentTaskRun().getState().getStartDate())
                            .thread(Thread.currentThread().getName())
                            .build()
                        );

                        executionQueue.emit(subflowExecution.getExecution());

                        // send a running worker task result to track running vs created status
                        if (subflowExecution.getParentTask().waitForExecution()) {
                            sendSubflowExecutionResult(execution, subflowExecution, subflowExecution.getParentTaskRun());
                        }
                    });
            }

            return Pair.of(
                executor,
                executorState
            );
        });

        if (result != null) {
            this.toExecution(result);
        }
    }

    private void sendSubflowExecutionResult(Execution execution, SubflowExecution<?> subflowExecution, TaskRun taskRun) {
        Flow workerTaskFlow = this.flowRepository.findByExecution(execution);

        ExecutableTask<?> executableTask = subflowExecution.getParentTask();

        RunContext runContext = runContextFactory.of(
            workerTaskFlow,
            subflowExecution.getParentTask(),
            execution,
            subflowExecution.getParentTaskRun()
        );
        try {
            Optional<SubflowExecutionResult> subflowExecutionResult = executableTask
                .createSubflowExecutionResult(runContext, taskRun, workerTaskFlow, execution);

            subflowExecutionResult.ifPresent(workerTaskResult -> this.subflowExecutionResultQueue.emit(workerTaskResult));
        } catch (Exception e) {
            log.error("Unable to create the Subflow Execution Result", e);
            // we send a fail subflow execution result to end the flow
            this.subflowExecutionResultQueue.emit(
                SubflowExecutionResult.builder()
                    .executionId(execution.getId())
                    .state(State.Type.FAILED)
                    .parentTaskRun(taskRun.withState(State.Type.FAILED).withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build())))
                    .build()
            );
        }
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage());
            return;
        }

        WorkerTaskResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getTaskRun().getExecutionId())) {
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
        if (skipExecutionService.skipExecution(message.getParentTaskRun().getExecutionId())) {
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

                    // iterative tasks
                    Task task = flow.findTaskByTaskId(message.getParentTaskRun().getTaskId());
                    TaskRun taskRun;
                    if (task instanceof ForEachItem forEachItem) {
                        RunContext runContext = runContextFactory.of(flow, task, current.getExecution(), message.getParentTaskRun());
                        taskRun = ExecutableUtils.manageIterations(
                            runContext.storage(),
                            message.getParentTaskRun(),
                            current.getExecution(),
                            forEachItem.getTransmitFailed(),
                            forEachItem.isAllowFailure()
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

        if (skipExecutionService.skipExecution(event.getExecutionId())) {
            log.warn("Skipping execution {}", event.getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, event);
        }

        // Immediately fire the event in EXECUTED state to notify the Workers to kill
        // any remaining tasks for that executing regardless of if the execution exist or not.
        // Note, that this event will be a noop if all tasks for that execution are already killed or completed.
        killQueue.emit(ExecutionKilled
            .builder()
            .executionId(event.getExecutionId())
            .isOnKillCascade(false)
            .state(ExecutionKilled.State.EXECUTED)
            .build()
        );

        Executor executor = mayTransitExecutionToKillingStateAndGet(event.getExecutionId());

        // Check whether kill event should be propagated to downstream executions.
        // By default, always propagate the ExecutionKill to sub-flows (for backward compatibility).
        Boolean isOnKillCascade = Optional.ofNullable(event.getIsOnKillCascade()).orElse(true);
        if (isOnKillCascade) {
            executionService
                .killSubflowExecutions(event.getTenantId(), event.getExecutionId())
                .doOnNext(killQueue::emit)
                .blockLast();
        }

        if (executor != null) {
            // Transmit the new execution state. Note that the execution
            // will eventually transition to KILLED state before sub-flow executions are actually killed.
            // This behavior is acceptable due to the fire-and-forget nature of the killing event.
            this.toExecution(executor);
        }
    }

    private Executor mayTransitExecutionToKillingStateAndGet(final String executionId) {
        return executionRepository.lock(executionId, pair -> {
            Execution currentExecution = pair.getLeft();
            Execution killing = executionService.kill(currentExecution);
            Executor current = new Executor(currentExecution, null)
                .withExecution(killing, "joinKillingExecution");
            return Pair.of(current, pair.getRight());
        });
    }

    private void toExecution(Executor executor) {
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

        // handle actions on terminated state
        // the terminated state can only come from the execution queue, and in this case we always have a flow in the executor
        if (executor.getFlow() != null && conditionService.isTerminatedWithListeners(executor.getFlow(), executor.getExecution())) {
            Execution execution = executor.getExecution();
            // handle flow triggers
            flowTriggerService.computeExecutionsFromFlowTriggers(execution, allFlows, Optional.of(multipleConditionStorage))
                .forEach(this.executionQueue::emit);

            // purge subflow execution storage
            subflowExecutionStorage.get(execution.getId())
                .ifPresent(subflowExecution -> {
                    // If we didn't wait for the flow execution, the worker task execution has already been created by the Executor service.
                    if (subflowExecution.getParentTask() != null && subflowExecution.getParentTask().waitForExecution()) {
                        sendSubflowExecutionResult(execution, subflowExecution, subflowExecution.getParentTaskRun().withState(execution.getState().getCurrent()));
                    }

                    subflowExecutionStorage.delete(subflowExecution);
                });

            // check if there exist a queued execution and submit it to the execution queue
            if (executor.getFlow().getConcurrency() != null && executor.getFlow().getConcurrency().getBehavior() == Concurrency.Behavior.QUEUE) {
                executionQueuedStorage.pop(executor.getFlow().getTenantId(),
                    executor.getFlow().getNamespace(),
                    executor.getFlow().getId(),
                    queued -> executionQueue.emit(queued.withState(State.Type.RUNNING))
                );
            }
        }
    }

    private Flow transform(Flow flow, Execution execution) {
        if (templateExecutorInterface.isPresent()) {
            try {
                flow = Template.injectTemplate(
                    flow,
                    execution,
                    (tenantId, namespace, id) -> templateExecutorInterface.get().findById(tenantId, namespace, id).orElse(null)
                );
            } catch (InternalException e) {
                log.warn("Failed to inject template", e);
            }
        }

        return taskDefaultService.injectDefaults(flow, execution);
    }

    private void executionDelaySend() {
        if (isShutdown) {
            return;
        }

        executionDelayStorage.get(executionDelay -> {
            Executor result = executionRepository.lock(executionDelay.getExecutionId(), pair -> {
                Executor executor = new Executor(pair.getLeft(), null);

                try {
                    if (executor.getExecution().findTaskRunByTaskRunId(executionDelay.getTaskRunId()).getState().getCurrent() == State.Type.PAUSED) {

                        Execution markAsExecution = executionService.markAs(
                            pair.getKey(),
                            executionDelay.getTaskRunId(),
                            executionDelay.getState()
                        );

                        executor = executor.withExecution(markAsExecution, "pausedRestart");
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
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue();

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
        String deduplicationKey = taskRun.getId();
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
        String deduplicationKey = taskRun.getId() + (taskRun.getIteration() == null ? "" : "-" + taskRun.getIteration());
        State.Type current = executorState.getSubflowExecutionDeduplication().get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.trace("Duplicate SubflowExecution on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
            return false;
        } else {
            executorState.getSubflowExecutionDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private Executor handleFailedExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);

        try {
            failedExecutionWithLog.getLogs().forEach(logQueue::emitAsync);
        } catch (Exception ex) {
            log.error("Failed to produce {}", e.getMessage(), ex);
        }

        return executor.withExecution(failedExecutionWithLog.getExecution(), "exception");
    }

    @Override
    public void close() throws IOException {
        isShutdown = true;
        scheduledDelay.shutdown();
        scheduledHeartbeat.shutdown();
    }
}
