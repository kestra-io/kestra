package io.kestra.runner.memory;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.core.tasks.flows.Template;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@MemoryQueueEnabled
@Slf4j
public class MemoryExecutor implements ExecutorInterface {
    private static final MemoryMultipleConditionStorage multipleConditionStorage = new MemoryMultipleConditionStorage();
    private static final ConcurrentHashMap<String, ExecutionState> EXECUTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, WorkerTaskExecution> WORKERTASKEXECUTIONS_WATCHER = new ConcurrentHashMap<>();
    private List<Flow> allFlows;
    private final ScheduledExecutorService schedulerDelay = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private ApplicationContext applicationContext;

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
    private FlowService flowService;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private Template.TemplateExecutorInterface templateExecutorInterface;

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

    @Override
    public void run() {
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        applicationContext.registerSingleton(new DefaultFlowExecutor(flowListeners, this.flowRepository));

        this.executionQueue.receive(MemoryExecutor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(MemoryExecutor.class, this::workerTaskResultQueue);
    }

    private void executionQueue(Execution message) {
        if (skipExecutionService.skipExecution(message.getId())) {
            log.warn("Skipping execution {}", message.getId());
            return;
        }

        if (message.getTaskRunList() == null || message.getTaskRunList().size() == 0 || message.getState().isCreated()) {
            this.handleExecution(saveExecution(message));
        }
    }

    private Flow transform(Flow flow, Execution execution) {
        try {
            flow = Template.injectTemplate(
                flow,
                execution,
                (namespace, id) -> templateExecutorInterface.findById(namespace, id).orElse(null)
            );
        } catch (InternalException e) {
            log.debug("Failed to inject template",  e);
        }

        return taskDefaultService.injectDefaults(flow, execution);
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

            if (executor.getNexts().size() > 0 && deduplicateNexts(execution, executor.getNexts())) {
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

            if (executor.getWorkerTasks().size() > 0) {
                List<WorkerTask> workerTasksDedup = executor.getWorkerTasks().stream()
                    .filter(workerTask -> this.deduplicateWorkerTask(execution, workerTask.getTaskRun()))
                    .collect(Collectors.toList());

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

            if (executor.getWorkerTaskResults().size() > 0) {
                executor.getWorkerTaskResults()
                    .forEach(workerTaskResultQueue::emit);
            }

            if (executor.getExecutionDelays().size() > 0) {
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

                                    if (executionState.execution.findTaskRunByTaskRunId(workerTaskResultDelay.getTaskRunId()).getState().getCurrent() == State.Type.PAUSED) {
                                        Execution markAsExecution = executionService.markAs(
                                            executionState.execution,
                                            workerTaskResultDelay.getTaskRunId(),
                                            workerTaskResultDelay.getState()
                                        );

                                        executionQueue.emit(markAsExecution);
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


            if (executor.getWorkerTaskExecutions().size() > 0) {
                executor.getWorkerTaskExecutions()
                    .forEach(workerTaskExecution -> {
                        WORKERTASKEXECUTIONS_WATCHER.put(workerTaskExecution.getExecution().getId(), workerTaskExecution);

                        executionQueue.emit(workerTaskExecution.getExecution());
                    });
            }

            // Listeners need the last emit
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                this.executionQueue.emit(execution);
            }

            // multiple condition
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                // multiple conditions storage
                multipleConditionStorage.save(
                    flowService
                        .multipleFlowTrigger(allFlows.stream(), flow, execution, multipleConditionStorage)
                );

                // Flow Trigger
                flowService
                    .flowTriggerExecution(allFlows.stream(), execution, multipleConditionStorage)
                    .forEach(this.executionQueue::emit);

                // Trigger is done, remove matching multiple condition
                flowService
                    .multipleFlowToDelete(allFlows.stream(), multipleConditionStorage)
                    .forEach(multipleConditionStorage::delete);
            }

            // worker task execution
            if (conditionService.isTerminatedWithListeners(flow, execution) && WORKERTASKEXECUTIONS_WATCHER.containsKey(execution.getId())) {
                WorkerTaskExecution workerTaskExecution = WORKERTASKEXECUTIONS_WATCHER.get(execution.getId());
                Flow workerTaskFlow = this.flowRepository.findByExecution(execution);

                WorkerTaskResult workerTaskResult = workerTaskExecution
                    .getTask()
                    .createWorkerTaskResult(runContextFactory, workerTaskExecution, workerTaskFlow, execution);

                this.workerTaskResultQueue.emit(workerTaskResult);

                WORKERTASKEXECUTIONS_WATCHER.remove(execution.getId());
            }
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
                return new ExecutionState(execution);
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

    private void workerTaskResultQueue(WorkerTaskResult message) {
        if (skipExecutionService.skipExecution(message.getTaskRun().getExecutionId())) {
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
                        return executionState.from(message, this.executorService, this.flowRepository);
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

    private boolean deduplicateWorkerTask(Execution execution, TaskRun taskRun) {
        ExecutionState executionState = EXECUTIONS.get(execution.getId());

        String deduplicationKey = taskRun.getExecutionId() + "-" + taskRun.getId();
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
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue();

                if (executionState.childDeduplication.containsKey(deduplicationKey)) {
                    log.trace("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
                    return false;
                } else {
                    executionState.childDeduplication.put(deduplicationKey, taskRun.getId());
                    return true;
                }
            });
    }



    private static class ExecutionState {
        private final Execution execution;
        private Map<String, TaskRun> taskRuns = new ConcurrentHashMap<>();
        private Map<String, State.Type> workerTaskDeduplication = new ConcurrentHashMap<>();
        private Map<String, String> childDeduplication = new ConcurrentHashMap<>();

        public ExecutionState(Execution execution) {
            this.execution = execution;
        }

        public ExecutionState(ExecutionState executionState, Execution execution) {
            this.execution = execution;
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

        public ExecutionState from(WorkerTaskResult workerTaskResult, ExecutorService executorService, FlowRepositoryInterface flowRepository) throws InternalException {
            this.taskRuns.compute(
                taskRunKey(workerTaskResult.getTaskRun()),
                (key, taskRun) -> workerTaskResult.getTaskRun()
            );

            Execution execution = executorService.addDynamicTaskRun(
                this.execution,
                flowRepository.findByExecution(this.execution),
                workerTaskResult
            );

            if (execution != null) {
                return new ExecutionState(this, execution);
            }

            return this;
        }
    }

    @Override
    public void close() throws IOException {
        executionQueue.close();
        workerTaskQueue.close();
        workerTaskResultQueue.close();
        logQueue.close();
    }
}
