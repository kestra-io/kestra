package org.kestra.runner.memory;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractExecutor;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.services.ConditionService;
import org.kestra.core.services.FlowService;
import org.kestra.core.services.TaskDefaultService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
@Prototype
@MemoryQueueEnabled
public class MemoryExecutor extends AbstractExecutor {
    private final FlowRepositoryInterface flowRepository;
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<WorkerTask> workerTaskQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final QueueInterface<LogEntry> logQueue;
    private final FlowService flowService;
    private static final MemoryMultipleConditionStorage multipleConditionStorage = new MemoryMultipleConditionStorage();

    private static final ConcurrentHashMap<String, ExecutionState> executions = new ConcurrentHashMap<>();
    private List<Flow> allFlows;

    @Inject
    public MemoryExecutor(
        RunContextFactory runContextFactory,
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        MetricRegistry metricRegistry,
        FlowService flowService,
        ConditionService conditionService,
        TaskDefaultService taskDefaultService
    ) {
        super(runContextFactory, metricRegistry, conditionService, taskDefaultService);

        this.flowRepository = flowRepository;
        this.executionQueue = executionQueue;
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.logQueue = logQueue;
        this.flowService = flowService;
        this.conditionService = conditionService;
    }

    @Override
    public void run() {
        this.allFlows = this.flowRepository.findAll();
        this.executionQueue.receive(MemoryExecutor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(MemoryExecutor.class, this::workerTaskResultQueue);
    }

    private void executionQueue(Execution message) {
        if (message.getTaskRunList() == null || message.getTaskRunList().size() == 0 || message.isJustRestarted()) {
            this.handleExecution(saveExecution(message));
        }
    }

    private void handleExecution(ExecutionState state) {
        synchronized (this) {
            if (log.isDebugEnabled()) {
                log.debug("Execution in with {}: {}", state.execution.toCrc32State(), state.execution.toStringState());
            }

            Flow flow = this.flowRepository.findByExecution(state.execution);

            Execution execution = state.execution;

            Optional<Execution> main = this.doMain(execution, flow);
            if (main.isPresent()) {
                this.toExecution(main.get());
                return;
            }

            try {
                Optional<List<TaskRun>> nexts = this.doNexts(execution, flow);
                if (nexts.isPresent() && deduplicateNexts(execution, nexts.get())) {
                    this.toExecution(this.onNexts(flow, execution, nexts.get()));
                    return;
                }

                Optional<List<WorkerTask>> workerTasks = this.doWorkerTask(execution, flow);
                if (workerTasks.isPresent()) {
                    List<WorkerTask> workerTasksDedup = workerTasks.stream()
                        .flatMap(Collection::stream)
                        .filter(workerTask -> this.deduplicateWorkerTask(execution, workerTask.getTaskRun()))
                        .collect(Collectors.toList());

                    // WorkerTask not flowable to workerTask
                    workerTasksDedup
                        .stream()
                        .filter(workerTask -> !workerTask.getTask().isFlowable())
                        .forEach(workerTaskQueue::emit);

                    // WorkerTask not flowable to workerTaskResult as Running
                    workerTasksDedup
                        .stream()
                        .filter(workerTask -> workerTask.getTask().isFlowable())
                        .map(workerTask -> new WorkerTaskResult(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING))))
                        .forEach(workerTaskResultQueue::emit);
                    return;
                }

                Optional<List<WorkerTaskResult>> workerTaskResults = this.doWorkerTaskResult(execution, flow);
                if (workerTaskResults.isPresent()) {
                    workerTaskResults.stream()
                        .flatMap(Collection::stream)
                        .forEach(workerTaskResultQueue::emit);

                    return;
                }
            } catch (Exception e) {
                handleFailedExecutionFromExecutor(execution, e);
                return;
            }

            // Listeners need the last emit
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                this.executionQueue.emit(execution);
            }

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
        }
    }

    private void handleFailedExecutionFromExecutor(Execution execution, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = execution.failedExecutionFromExecutor(e);
        try {
            log.error("Failed from executor with '{}'", e.getMessage(), e);

            failedExecutionWithLog.getLogs().forEach(logQueue::emit);

            this.toExecution(failedExecutionWithLog.getExecution());
        } catch (Exception ex) {
            log.error("Failed to produce {}", e.getMessage(), ex);
        }
    }

    private ExecutionState saveExecution(Execution execution) {
        ExecutionState queued;
        queued = executions.compute(execution.getId(), (s, executionState) -> {
            if (executionState == null) {
                return new ExecutionState(execution);
            } else {
                return executionState.from(execution);
            }
        });

        return queued;
    }

    private void toExecution(Execution execution) {
        Flow flow = this.flowRepository.findByExecution(execution);

        if (log.isDebugEnabled()) {
            log.debug("Execution out with {}: {}", execution.toCrc32State(), execution.toStringState());
        }

        // emit for other consumer than executor
        this.executionQueue.emit(execution);

        // recursive search for other executor
        this.handleExecution(saveExecution(execution));

        // delete if ended
        if (conditionService.isTerminatedWithListeners(flow, execution)) {
            executions.remove(execution.getId());
        }
    }

    private void workerTaskResultQueue(WorkerTaskResult message) {
        synchronized (this) {
            if (log.isDebugEnabled()) {
                log.debug("WorkerTaskResult: {}", message.getTaskRun().toStringState());
            }

            metricRegistry
                .counter(MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                .increment();

            metricRegistry
                .timer(MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                .record(message.getTaskRun().getState().getDuration());

            // save WorkerTaskResult on current QueuedExecution
            executions.compute(message.getTaskRun().getExecutionId(), (s, executionState) -> {
                if (executionState == null) {
                    throw new IllegalStateException("Execution state don't exist for " + s + ", receive " + message);
                }

                if (executionState.execution.hasTaskRunJoinable(message.getTaskRun())) {
                     return executionState.from(message);
                } else {
                    return executionState;
                }
            });

            this.toExecution(executions.get(message.getTaskRun().getExecutionId()).execution);
        }
    }

    private void handleFlowTrigger(Execution execution) {


    }

    private boolean deduplicateWorkerTask(Execution execution, TaskRun taskRun) {
        ExecutionState executionState = executions.get(execution.getId());

        String deduplicationKey = taskRun.getExecutionId() + "-" + taskRun.getId();
        State.Type current = executionState.workerTaskDeduplication.get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.debug("Duplicate WorkerTask on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
            return false;
        } else {
            executionState.workerTaskDeduplication.put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private boolean deduplicateNexts(Execution execution, List<TaskRun> taskRuns) {
        ExecutionState executionState = executions.get(execution.getId());

        return taskRuns
            .stream()
            .anyMatch(taskRun -> {
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue();

                if (executionState.childDeduplication.containsKey(deduplicationKey)) {
                    log.debug("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
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

        public ExecutionState from(WorkerTaskResult workerTaskResult) {
            this.taskRuns.compute(
                taskRunKey(workerTaskResult.getTaskRun()),
                (key, taskRun) -> workerTaskResult.getTaskRun()
            );

            return this;
        }
    }
}
