package io.kestra.runner.memory;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.plugin.core.flow.ForEachItem;
import io.kestra.plugin.core.flow.Template;
import io.kestra.core.utils.Either;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@MemoryQueueEnabled
@Slf4j
public class MemoryExecutor implements ExecutorInterface {
    private static final ConcurrentHashMap<String, ExecutionState> EXECUTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SubflowExecution<?>> SUBFLOWEXECUTIONS_WATCHER = new ConcurrentHashMap<>();
    private List<Flow> allFlows;
    private final ScheduledExecutorService schedulerDelay = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private FlowRepositoryInterface flowRepository;

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
    private PluginDefaultService pluginDefaultService;

    @Inject
    private Optional<Template.TemplateExecutorInterface> templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ConditionService conditionService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private ExecutionService executionService;

    @Inject
    protected FlowListenersInterface flowListeners;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private AbstractFlowTriggerService flowTriggerService;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    private final MultipleConditionStorageInterface multipleConditionStorage = new MemoryMultipleConditionStorage();

    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;

    @Override
    public void run() {
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        this.executionQueue.receive(MemoryExecutor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(MemoryExecutor.class, this::workerTaskResultQueue);
        this.killQueue.receive(MemoryExecutor.class, this::killQueue);
        this.subflowExecutionResultQueue.receive(Executor.class, this::subflowExecutionResultQueue);
    }

    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize the execution: {}", either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();
        if (skipExecutionService.skipExecution(message)) {
            log.warn("Skipping execution {}", message.getId());
            return;
        }

        if (message.getTaskRunList() == null ||
            message.getTaskRunList().isEmpty()||
            message.getState().isCreated() ||
            message.getState().getHistories().get(message.getState().getHistories().size()-2).getState().equals(State.Type.RETRYING)) {
            this.handleExecution(saveExecution(message));
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
                log.debug("Failed to inject template", e);
            }
        }

        return pluginDefaultService.injectDefaults(flow, execution);
    }

    private void handleExecution(ExecutionState state) {
        synchronized (this) {
            final Flow flow = transform(this.flowRepository.findByExecution(state.execution), state.execution);

            Execution execution = state.execution;
            Executor executor = new Executor(execution, null).withFlow(flow);

            if (log.isDebugEnabled()) {
                executorService.log(log, true, executor);
            }

            executor = executorService.process(executor);

            if (!executor.getNexts().isEmpty() && deduplicateNexts(execution, executor.getNexts())) {
                executor.withExecution(
                    executorService.onNexts(executor.getFlow(), executor.getExecution(), executor.getNexts()),
                    "onNexts"
                );
            }

            if (executor.getException() != null) {
                handleFailedExecutionFromExecutor(executor, executor.getException());
            } else if (executor.isExecutionUpdated()) {
                toExecution(executor);
            }

            if (!executor.getWorkerTasks().isEmpty()) {
                List<WorkerTask> workerTasksDedup = executor.getWorkerTasks().stream()
                    .filter(workerTask -> this.deduplicateWorkerTask(execution, workerTask.getTaskRun()))
                    .toList();

                // Send WorkerTask not flowable to the worker
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isSendToWorkerTask())
                    .forEach(workerTaskQueue::emit);

                // Move WorkerTask flowable to RUNNING and send them directly to the workerTaskResult
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isFlowable())
                    .map(workerTask -> new WorkerTaskResult(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING))))
                    .forEach(workerTaskResultQueue::emit);
            }

            if (!executor.getWorkerTaskResults().isEmpty()) {
                executor.getWorkerTaskResults()
                    .forEach(workerTaskResultQueue::emit);
            }

            // subflow execution results
            if (!executor.getSubflowExecutionResults().isEmpty()) {
                executor.getSubflowExecutionResults()
                    .forEach(subflowExecutionResultQueue::emit);
            }

            if (!executor.getExecutionDelays().isEmpty()) {
                executor.getExecutionDelays()
                    .forEach(workerTaskResultDelay -> {
                        long between = ChronoUnit.MICROS.between(Instant.now(), workerTaskResultDelay.getDate());

                        if (between <= 0) {
                            between = 1;
                        }

                        schedulerDelay.schedule(
                            () -> {
                                try {
                                    ExecutionState executionState = EXECUTIONS.get(workerTaskResultDelay.getExecutionId());

                                    if (workerTaskResultDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW)) {
                                        Execution markAsExecution = executionService.markAs(
                                            executionState.execution,
                                            flow,
                                            workerTaskResultDelay.getTaskRunId(),
                                            workerTaskResultDelay.getState()
                                        );
                                        EXECUTIONS.put(workerTaskResultDelay.getExecutionId(), executionState.from(markAsExecution));
                                        executionQueue.emit(markAsExecution);
                                    } else if (workerTaskResultDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)) {
                                        Execution newAttempt = executionService.retryTask(
                                            executionState.execution,
                                            workerTaskResultDelay.getTaskRunId()
                                        );

                                        EXECUTIONS.put(workerTaskResultDelay.getExecutionId(), executionState.from(newAttempt));
                                        executionQueue.emit(newAttempt);
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            between,
                            TimeUnit.MICROSECONDS
                        );
                    });
            }


            if (!executor.getSubflowExecutions().isEmpty()) {
                executor.getSubflowExecutions()
                    .forEach(subflowExecution -> {
                        SUBFLOWEXECUTIONS_WATCHER.put(subflowExecution.getExecution().getId(), subflowExecution);

                        executionQueue.emit(subflowExecution.getExecution());

                        // send a running worker task result to track running vs created status
                        if (subflowExecution.getParentTask().waitForExecution()) {
                            sendSubflowExecutionResult(execution, subflowExecution, subflowExecution.getParentTaskRun());
                        }
                    });
            }

            // Listeners need the last emit
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                this.executionQueue.emit(execution);
            }

            // multiple condition
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                flowTriggerService.computeExecutionsFromFlowTriggers(execution, allFlows, Optional.of(multipleConditionStorage))
                    .forEach(this.executionQueue::emit);
            }

            // worker task execution
            if (conditionService.isTerminatedWithListeners(flow, execution) && SUBFLOWEXECUTIONS_WATCHER.containsKey(execution.getId())) {
                SubflowExecution<?> subflowExecution = SUBFLOWEXECUTIONS_WATCHER.get(execution.getId());

                // If we didn't wait for the flow execution, the worker task execution has already been created by the Executor service.
                if (subflowExecution.getParentTask().waitForExecution()) {
                    sendSubflowExecutionResult(execution, subflowExecution, subflowExecution.getParentTaskRun().withState(execution.getState().getCurrent()));
                }

                SUBFLOWEXECUTIONS_WATCHER.remove(execution.getId());
            }
        }
    }

    private void sendSubflowExecutionResult(Execution execution, SubflowExecution<?> subflowExecution, TaskRun taskRun) {
        try {
            Flow workerTaskFlow = this.flowRepository.findByExecution(execution);

            ExecutableTask<?> executableTask = subflowExecution.getParentTask();

            RunContext runContext = runContextFactory.of(
                workerTaskFlow,
                subflowExecution.getParentTask(),
                execution,
                subflowExecution.getParentTaskRun()
            );

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

    private void handleFailedExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);
        try {
            failedExecutionWithLog.getLogs().forEach(logQueue::emit);

            this.toExecution(executor.withExecution(failedExecutionWithLog.getExecution(), "exception"));
        } catch (Exception ex) {
            log.error("Failed to produce {}", e.getMessage(), ex);
        }
    }

    private ExecutionState saveExecution(Execution execution) {
        ExecutionState queued;
        queued = EXECUTIONS.compute(execution.getId(), (s, executionState) -> {
            if (executionState == null) {
                return new ExecutionState(runContextFactory, execution);
            } else {
                return executionState.from(execution);
            }
        });

        return queued;
    }

    private void toExecution(Executor executor) {
        if (log.isDebugEnabled()) {
            executorService.log(log, false, executor);
        }

        // emit for other consumer than executor
        this.executionQueue.emit(executor.getExecution());

        // recursive search for other executor
        this.handleExecution(saveExecution(executor.getExecution()));

        // delete if ended
        if (executorService.canBePurged(executor)) {
            EXECUTIONS.remove(executor.getExecution().getId());
        }
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize the worker task result: {}", either.getRight().getMessage());
            return;
        }

        WorkerTaskResult message = either.getLeft();

        if (skipExecutionService.skipExecution(message.getTaskRun())) {
            log.warn("Skipping execution {}", message.getTaskRun().getExecutionId());
            return;
        }

        synchronized (this) {
            if (log.isDebugEnabled()) {
                executorService.log(log, true, message);
            }

            // send metrics on terminated
            if (message.getTaskRun().getState().isTerminated()) {
                metricRegistry
                    .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                    .increment();

                metricRegistry
                    .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                    .record(message.getTaskRun().getState().getDuration());
            }

            // save WorkerTaskResult on current QueuedExecution
            EXECUTIONS.compute(message.getTaskRun().getExecutionId(), (s, executionState) -> {
                if (executionState == null) {
                    throw new IllegalStateException("Execution state don't exist for " + s + ", receive " + message);
                }

                if (executionState.execution.hasTaskRunJoinable(message.getTaskRun())) {
                    try {
                        return executionState.from(message, this.executorService, this.executionService, this.flowRepository);
                    } catch (InternalException e) {
                        return new ExecutionState(executionState, executionState.execution.failedExecutionFromExecutor(e).getExecution());
                    }
                } else {
                    return executionState;
                }
            });

            Flow flow = this.flowRepository.findByExecution(EXECUTIONS.get(message.getTaskRun().getExecutionId()).execution);
            flow = transform(flow, EXECUTIONS.get(message.getTaskRun().getExecutionId()).execution);

            this.toExecution(new Executor(EXECUTIONS.get(message.getTaskRun().getExecutionId()).execution, null).withFlow(flow));
        }
    }

    private void subflowExecutionResultQueue(Either<SubflowExecutionResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize the worker task result: {}", either.getRight().getMessage());
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

        synchronized (this) {
            if (log.isDebugEnabled()) {
                executorService.log(log, true, message);
            }

            // send metrics on terminated
            if (message.getParentTaskRun().getState().isTerminated()) {
                metricRegistry
                    .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                    .increment();

                metricRegistry
                    .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                    .record(message.getParentTaskRun().getState().getDuration());
            }

            // save subflow execution result on current QueuedExecution
            EXECUTIONS.compute(message.getParentTaskRun().getExecutionId(), (s, executionState) -> {
                if (executionState == null) {
                    throw new IllegalStateException("Execution state don't exist for " + s + ", receive " + message);
                }

                if (executionState.execution.hasTaskRunJoinable(message.getParentTaskRun())) {
                    try {
                        return executionState.from(message, this.flowRepository);
                    } catch (InternalException e) {
                        return new ExecutionState(executionState, executionState.execution.failedExecutionFromExecutor(e).getExecution());
                    }
                } else {
                    return executionState;
                }
            });

            Flow flow = this.flowRepository.findByExecution(EXECUTIONS.get(message.getParentTaskRun().getExecutionId()).execution);
            flow = transform(flow, EXECUTIONS.get(message.getParentTaskRun().getExecutionId()).execution);

            this.toExecution(new Executor(EXECUTIONS.get(message.getParentTaskRun().getExecutionId()).execution, null).withFlow(flow));
        }
    }

    private boolean deduplicateWorkerTask(Execution execution, TaskRun taskRun) {
        ExecutionState executionState = EXECUTIONS.get(execution.getId());

        String deduplicationKey = taskRun.getExecutionId() + "-" + taskRun.getId() + "-" + taskRun.attemptNumber();
        State.Type current = executionState.workerTaskDeduplication.get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.trace("Duplicate WorkerTask on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
            return false;
        } else {
            executionState.workerTaskDeduplication.put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private boolean deduplicateNexts(Execution execution, List<TaskRun> taskRuns) {
        ExecutionState executionState = EXECUTIONS.get(execution.getId());

        return taskRuns
            .stream()
            .anyMatch(taskRun -> {
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue() + taskRun.attemptNumber();

                if (executionState.childDeduplication.containsKey(deduplicationKey)) {
                    log.trace("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
                    return false;
                } else {
                    executionState.childDeduplication.put(deduplicationKey, taskRun.getId());
                    return true;
                }
            });
    }

    private void killQueue(Either<ExecutionKilled, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a killed execution: {}", either.getRight().getMessage());
            return;
        }

        if (!(either.getLeft() instanceof ExecutionKilledExecution message)) {
            return;
        }

        if (skipExecutionService.skipExecution(message.getExecutionId())) {
            log.warn("Skipping execution {}", message.getExecutionId());
            return;
        }

        synchronized (this) {
            if (log.isDebugEnabled()) {
                executorService.log(log, true, message);
            }

            final Flow flowFromRepository = this.flowRepository.findByExecution(EXECUTIONS.get(message.getExecutionId()).execution);

            // save WorkerTaskResult on current QueuedExecution
            EXECUTIONS.compute(message.getExecutionId(), (s, executionState) -> {
                if (executionState == null) {
                    throw new IllegalStateException("Execution state don't exist for " + s + ", receive " + message);
                }

                return executionState.from(executionService.kill(executionState.execution, flowFromRepository));
            });

            Flow flow = transform(flowFromRepository, EXECUTIONS.get(message.getExecutionId()).execution);

            this.toExecution(new Executor(EXECUTIONS.get(message.getExecutionId()).execution, null).withFlow(flow));
        }
    }


    private static class ExecutionState {

        private RunContextFactory runContextFactory;
        private final Execution execution;
        private Map<String, TaskRun> taskRuns = new ConcurrentHashMap<>();
        private Map<String, State.Type> workerTaskDeduplication = new ConcurrentHashMap<>();
        private Map<String, String> childDeduplication = new ConcurrentHashMap<>();

        public ExecutionState(RunContextFactory runContextFactory, Execution execution) {
            this.execution = execution;
            this.runContextFactory = runContextFactory;
        }

        public ExecutionState(ExecutionState executionState, Execution execution) {
            this(executionState.runContextFactory, execution);
            this.taskRuns = executionState.taskRuns;
            this.workerTaskDeduplication = executionState.workerTaskDeduplication;
            this.childDeduplication = executionState.childDeduplication;
        }

        private static String taskRunKey(TaskRun taskRun) {
            return taskRun.getId() + "-" + (taskRun.getValue() == null ? "null" : taskRun.getValue());
        }

        public ExecutionState from(Execution execution) {
            List<TaskRun> taskRuns = execution.getTaskRunList()
                .stream()
                .map(taskRun -> {
                    if (!this.taskRuns.containsKey(taskRunKey(taskRun))) {
                        return taskRun;
                    } else {
                        TaskRun stateTaskRun = this.taskRuns.get(taskRunKey(taskRun));

                        if (execution.hasTaskRunJoinable(stateTaskRun)) {
                            return stateTaskRun;
                        } else {
                            return taskRun;
                        }
                    }
                })
                .collect(Collectors.toList());

            Execution newExecution = execution.withTaskRunList(taskRuns);

            return new ExecutionState(this, newExecution);
        }

        public ExecutionState from(WorkerTaskResult workerTaskResult, ExecutorService executorService, ExecutionService executionService, FlowRepositoryInterface flowRepository) throws InternalException {
            Flow flow = flowRepository.findByExecution(this.execution);

            TaskRun taskRun = workerTaskResult.getTaskRun();
            this.taskRuns.compute(
                taskRunKey(taskRun),
                (key, value) -> taskRun
            );

            // dynamic tasks
            Execution execution = executorService.addDynamicTaskRun(
                this.execution,
                flow,
                workerTaskResult
            );

            // If the worker task result is killed, we must check if it has a parents to also kill them if not already done.
            // Running flowable tasks that have child tasks running in the worker will be killed thanks to that.
            if (taskRun.getState().getCurrent() == State.Type.KILLED && taskRun.getParentTaskRunId() != null) {
                execution = executionService.killParentTaskruns(taskRun, execution);
            }

            if (execution != null) {
                return new ExecutionState(this, execution);
            }

            return this;
        }

        public ExecutionState from(SubflowExecutionResult subflowExecutionResult, FlowRepositoryInterface flowRepository) throws InternalException {
            Flow flow = flowRepository.findByExecution(this.execution);

            // iterative tasks
            Task task = flow.findTaskByTaskId(subflowExecutionResult.getParentTaskRun().getTaskId());
            TaskRun taskRun;
            if (task instanceof ForEachItem.ForEachItemExecutable forEachItem) {
                RunContext runContext = runContextFactory.of(
                    flow,
                    task,
                    execution,
                    subflowExecutionResult.getParentTaskRun()
                );
                taskRun = ExecutableUtils.manageIterations(
                    runContext.storage(),
                    subflowExecutionResult.getParentTaskRun(),
                    this.execution,
                    forEachItem.getTransmitFailed(),
                    forEachItem.isAllowFailure()
                );
            } else {
                taskRun = subflowExecutionResult.getParentTaskRun();
            }

            this.taskRuns.compute(
                taskRunKey(taskRun),
                (key, value) -> taskRun
            );

            return this;
        }
    }

    @Override
    public void close() throws IOException {
        schedulerDelay.shutdown();
    }
}
