package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.LogService;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.flow.WaitFor;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class ExecutorService {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private ConditionService conditionService;

    @Inject
    private LogService logService;

    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    private WorkerGroupExecutorInterface workerGroupExecutorInterface;

    protected FlowExecutorInterface flowExecutorInterface;

    @Inject
    private ExecutionService executionService;

    protected FlowExecutorInterface flowExecutorInterface() {
        // bean is injected late, so we need to wait
        if (this.flowExecutorInterface == null) {
            this.flowExecutorInterface = applicationContext.getBean(FlowExecutorInterface.class);
        }

        return this.flowExecutorInterface;
    }

    public Executor checkConcurrencyLimit(Executor executor, Flow flow, Execution execution, long count) {
        // if above the limit, handle concurrency limit based on its behavior
        if (count >= flow.getConcurrency().getLimit()) {
            return switch (flow.getConcurrency().getBehavior()) {
                case QUEUE -> {
                    var newExecution = execution.withState(State.Type.QUEUED);

                    ExecutionRunning executionRunning = ExecutionRunning.builder()
                        .tenantId(flow.getTenantId())
                        .namespace(flow.getNamespace())
                        .flowId(flow.getId())
                        .execution(newExecution)
                        .concurrencyState(ExecutionRunning.ConcurrencyState.QUEUED)
                        .build();

                    // when max concurrency is reached, we throttle the execution and stop processing
                    logService.logExecution(
                        newExecution,
                        flow.logger(),
                        Level.INFO,
                        "Flow is queued due to concurrency limit exceeded, {} running(s)",
                        count
                    );
                    // return the execution queued
                    yield executor
                        .withExecutionRunning(executionRunning)
                        .withExecution(newExecution, "checkConcurrencyLimit");
                }
                case CANCEL ->
                    executor.withExecution(execution.withState(State.Type.CANCELLED), "checkConcurrencyLimit");
                case FAIL ->
                    executor.withException(new IllegalStateException("Flow is FAILED due to concurrency limit exceeded"), "checkConcurrencyLimit");
            };
        }

        // if under the limit, update the executor with a RUNNING ExecutionRunning to track them
        var executionRunning = new ExecutionRunning(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            executor.getExecution(),
            ExecutionRunning.ConcurrencyState.RUNNING
        );
        return executor.withExecutionRunning(executionRunning);
    }

    public Executor process(Executor executor) {
        // previous failed (flow join can fail), just forward
        // or concurrency limit failed/cancelled the execution
        if (!executor.canBeProcessed() || conditionService.isTerminatedWithListeners(executor.getFlow(), executor.getExecution())) {
            return executor;
        }

        try {
            executor = this.handleRestart(executor);
            executor = this.handleEnd(executor);
            // if killing: move created tasks to killed as they are not already started
            executor = this.handleCreatedKilling(executor);
            //then set the execution to killed
            executor = this.handleKilling(executor);

            // process next task if not killing or killed
            if (executor.getExecution().getState().getCurrent() != State.Type.KILLING && executor.getExecution().getState().getCurrent() != State.Type.KILLED && executor.getExecution().getState().getCurrent() != State.Type.QUEUED) {
                executor = this.handleNext(executor);
                executor = this.handleChildNext(executor);
            }

            // but keep listeners on killing
            executor = this.handleListeners(executor);

            // search for worker task
            executor = this.handleWorkerTask(executor);

            // search for worker task result
            executor = this.handleChildWorkerTaskResult(executor);

            // search for execution updating tasks
            executor = this.handleExecutionUpdatingTask(executor);

            // search for flow task
            executor = this.handleExecutableTask(executor);
        } catch (Exception e) {
            return executor.withException(e, "process");
        }

        return executor;
    }

    public Execution onNexts(Flow flow, Execution execution, List<TaskRun> nexts) {
        if (log.isTraceEnabled()) {
            logService.logExecution(
                execution,
                flow.logger(),
                Level.TRACE,
                "Found {} next(s) {}",
                nexts.size(),
                nexts
            );
        }

        List<TaskRun> executionTasksRun;
        Execution newExecution;

        if (execution.getTaskRunList() == null) {
            executionTasksRun = nexts;
        } else {
            executionTasksRun = new ArrayList<>(execution.getTaskRunList());
            executionTasksRun.addAll(nexts);
        }

        // update Execution
        newExecution = execution.withTaskRunList(executionTasksRun);

        if (execution.getState().getCurrent() == State.Type.CREATED) {
            metricRegistry
                .counter(MetricRegistry.EXECUTOR_EXECUTION_STARTED_COUNT, metricRegistry.tags(execution))
                .increment();

            logService.logExecution(
                execution,
                flow.logger(),
                Level.INFO,
                "Flow started"
            );

            newExecution = newExecution.withState(State.Type.RUNNING);
        }

        metricRegistry
            .counter(MetricRegistry.EXECUTOR_TASKRUN_NEXT_COUNT, metricRegistry.tags(execution))
            .increment(nexts.size());

        return newExecution;
    }

    private Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun parentTaskRun) throws InternalException {
        Task parent = flow.findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask<?> flowableParent) {

            RunContext runContext = runContextFactory.of(flow, parent, execution, parentTaskRun);

            // first find the normal ended child tasks and send result
            Optional<State.Type> state;
            try {
                state = flowableParent.resolveState(runContext, execution, parentTaskRun);
            } catch (Exception e) {
                // This will lead to the next task being still executed but at least Kestra will not crash.
                // This is the best we can do, Flowable task should not fail, so it's a kind of panic mode.
                runContext.logger().error("Unable to resolve state from the Flowable task: " + e.getMessage(), e);
                state = Optional.of(State.Type.FAILED);
            }
            Optional<WorkerTaskResult> endedTask = childWorkerTaskTypeToWorkerTask(
                state,
                parentTaskRun
            );

            if (endedTask.isPresent()) {
                WorkerTaskResult workerTaskResult = endedTask.get();
                // Compute outputs for the parent Flowable task if a terminated state was resolved
                if (workerTaskResult.getTaskRun().getState().isTerminated()) {
                    try {
                        Output outputs = flowableParent.outputs(runContext);
                        return Optional.of(new WorkerTaskResult(workerTaskResult
                            .getTaskRun()
                            .withOutputs(outputs != null ? outputs.toMap() : ImmutableMap.of()))
                        );
                    } catch (Exception e) {
                        runContext.logger().error("Unable to resolve outputs from the Flowable task: {}", e.getMessage(), e);
                    }
                }
                return endedTask;
            }

            // after if the execution is KILLING, we find if all already started tasks if finished
            if (execution.getState().getCurrent() == State.Type.KILLING) {
                // first notified the parent taskRun of killing to avoid new creation of tasks
                if (parentTaskRun.getState().getCurrent() != State.Type.KILLING) {
                    return childWorkerTaskTypeToWorkerTask(
                        Optional.of(State.Type.KILLING),
                        parentTaskRun
                    );
                }

                // Then wait for completion (KILLED or whatever) on child tasks to KILLED the parent one.
                List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
                    flowableParent.childTasks(runContext, parentTaskRun),
                    FlowableUtils.resolveTasks(flowableParent.getErrors(), parentTaskRun)
                );

                List<TaskRun> taskRunByTasks = execution.findTaskRunByTasks(currentTasks, parentTaskRun);

                if (taskRunByTasks.stream().filter(t -> t.getState().isTerminated()).count() == taskRunByTasks.size()) {
                    return childWorkerTaskTypeToWorkerTask(
                        Optional.of(State.Type.KILLED),
                        parentTaskRun
                    );
                }
            }
        }

        return Optional.empty();
    }

    private Optional<WorkerTaskResult> childWorkerTaskTypeToWorkerTask(
        Optional<State.Type> findState,
        TaskRun taskRun
    ) {
        return findState
            .map(throwFunction(type -> new WorkerTaskResult(taskRun.withState(type))))
            .stream()
            .peek(workerTaskResult -> {
                metricRegistry
                    .counter(
                        MetricRegistry.EXECUTOR_WORKERTASKRESULT_COUNT,
                        metricRegistry.tags(workerTaskResult)
                    )
                    .increment();

            })
            .findFirst();
    }

    private List<TaskRun> childNextsTaskRun(Executor executor, TaskRun parentTaskRun) throws InternalException {
        Task parent = executor.getFlow().findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask<?> flowableParent) {

            try {
                List<NextTaskRun> nexts = flowableParent.resolveNexts(
                    runContextFactory.of(
                        executor.getFlow(),
                        parent,
                        executor.getExecution(),
                        parentTaskRun
                    ),
                    executor.getExecution(),
                    parentTaskRun
                );

                if (!nexts.isEmpty()) {
                    return nexts.stream()
                        .map(throwFunction(NextTaskRun::getTaskRun))
                        .toList();
                }
            } catch (Exception e) {
                log.warn("Unable to resolve the next tasks to run", e);
            }
        }

        return Collections.emptyList();
    }

    private List<TaskRun> saveFlowableOutput(
        List<NextTaskRun> nextTaskRuns,
        Executor executor
    ) {
        return nextTaskRuns
            .stream()
            .map(throwFunction(t -> {
                TaskRun taskRun = t.getTaskRun();

                if (!(t.getTask() instanceof FlowableTask)) {
                    return taskRun;
                }
                FlowableTask<?> flowableTask = (FlowableTask<?>) t.getTask();

                try {
                    RunContext runContext = runContextFactory.of(
                        executor.getFlow(),
                        t.getTask(),
                        executor.getExecution(),
                        t.getTaskRun()
                    );

                    Output outputs = flowableTask.outputs(runContext);
                    taskRun = taskRun.withOutputs(outputs != null ? outputs.toMap() : ImmutableMap.of());
                } catch (Exception e) {
                    executor.getFlow().logger().warn("Unable to save output on taskRun '{}'", taskRun, e);
                }

                return taskRun;
            }))
            .toList();
    }

    private Executor onEnd(Executor executor) {
        final Flow flow = executor.getFlow();

        Logger logger = flow.logger();

        Execution newExecution = executor.getExecution()
            .withState(executor.getExecution().guessFinalState(flow));

        if (flow.getOutputs() != null) {
            RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
            try {
                Map<String, Object> outputs = flow.getOutputs()
                    .stream()
                    .collect(HashMap::new, (map, entry) -> {
                        final ObjectMapper mapper = new ObjectMapper();
                        final HashMap<String, Object> entryInfo = new HashMap<>();
                        entryInfo.put("value", entry.getValue());
                        entryInfo.put("displayName", entry.getDisplayName());
                        try {
                            map.put(entry.getId(), mapper.writeValueAsString(entryInfo));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }, Map::putAll);
                outputs = runContext.render(outputs);
                outputs = flowInputOutput.typedOutputs(flow, executor.getExecution(), outputs);
                newExecution = newExecution.withOutputs(outputs);
            } catch (Exception e) {
                logService.logExecution(
                    executor.getExecution(),
                    logger,
                    Level.ERROR,
                    "Failed to render output values",
                    e
                );
                runContext.logger().error("Failed to render output values: {}", e.getMessage(), e);
                newExecution = newExecution.withState(State.Type.FAILED);
            }
        }

        logService.logExecution(
            newExecution,
            flow.logger(),
            Level.INFO,
            "Flow completed with state {} in {}",
            newExecution.getState().getCurrent(),
            newExecution.getState().humanDuration()
        );

        if (logger.isTraceEnabled()) {
            logger.trace(newExecution.toString(true));
        }

        metricRegistry
            .counter(MetricRegistry.EXECUTOR_EXECUTION_END_COUNT, metricRegistry.tags(newExecution))
            .increment();

        metricRegistry
            .timer(MetricRegistry.EXECUTOR_EXECUTION_DURATION, metricRegistry.tags(newExecution))
            .record(newExecution.getState().getDuration());

        return executor.withExecution(newExecution, "onEnd");
    }

    private Executor handleNext(Executor executor) {
        List<NextTaskRun> nextTaskRuns = FlowableUtils
            .resolveSequentialNexts(
                executor.getExecution(),
                ResolvedTask.of(executor.getFlow().getTasks()),
                ResolvedTask.of(executor.getFlow().getErrors())
            );

        if (nextTaskRuns.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(
            // TODO - saveFlowableOutput seems to be only useful for Template
            this.saveFlowableOutput(nextTaskRuns, executor),
            "handleNext"
        );
    }

    private Executor handleChildNext(Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<TaskRun> running = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .toList();

        // Remove functional style to avoid (class io.kestra.core.exceptions.IllegalVariableEvaluationException cannot be cast to class java.lang.RuntimeException'
        ArrayList<TaskRun> result = new ArrayList<>();

        for (TaskRun taskRun : running) {
            result.addAll(this.childNextsTaskRun(executor, taskRun));
        }

        if (result.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(result, "handleChildNext");
    }

    private Executor handleChildWorkerTaskResult(Executor executor) throws Exception {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<WorkerTaskResult> list = new ArrayList<>();
        List<ExecutionDelay> executionDelays = new ArrayList<>();

        for (TaskRun taskRun : executor.getExecution().getTaskRunList()) {
            if (taskRun.getState().isRunning()) {
                Optional<WorkerTaskResult> workerTaskResult = this.childWorkerTaskResult(
                    executor.getFlow(),
                    executor.getExecution(),
                    taskRun
                );

                workerTaskResult.ifPresent(list::add);
            }

            Task task = executor.getFlow().findTaskByTaskIdOrNull(taskRun.getTaskId());
            String taskId = taskRun.getTaskId();
            Task parentTask = null;
            if (taskRun.getParentTaskRunId() != null) {
                do {
                    parentTask = executor.getFlow().findParentTasksByTaskId(taskId);
                    if (parentTask != null) {
                        taskId = parentTask.getId();
                    }
                } while (parentTask != null && parentTask.getRetry() == null);
            }

            /*
             * Check if the task is failed and if it has a retry policy
             */
            if (!executor.getExecution().getState().isRetrying() &&
                taskRun.getState().isFailed() &&
                (task instanceof RunnableTask<?> || task instanceof Subflow) &&
                (task.getRetry() != null || executor.getFlow().getRetry() != null || (parentTask != null && parentTask.getRetry() != null))
            ) {
                Instant nextRetryDate;
                AbstractRetry retry;
                AbstractRetry.Behavior behavior;

                // Case task has a retry
                if (task.getRetry() != null) {
                    retry = task.getRetry();
                    behavior = retry.getBehavior();
                    nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                        taskRun.nextRetryDate(retry, executor.getExecution()) :
                        taskRun.nextRetryDate(retry);
                }
                // Case parent task has a retry
                else if (parentTask != null && parentTask.getRetry() != null) {
                    retry = parentTask.getRetry();
                    behavior = retry.getBehavior();
                    nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                        taskRun.nextRetryDate(retry, executor.getExecution()) :
                        taskRun.nextRetryDate(retry);
                }
                // Case flow has a retry
                else {
                    retry = executor.getFlow().getRetry();
                    behavior = retry.getBehavior();
                    nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                        executionService.nextRetryDate(retry, executor.getExecution()) :
                        taskRun.nextRetryDate(retry);
                }
                if (nextRetryDate != null) {
                    ExecutionDelay.ExecutionDelayBuilder executionDelayBuilder = ExecutionDelay.builder()
                        .taskRunId(taskRun.getId())
                        .executionId(executor.getExecution().getId())
                        .date(nextRetryDate)
                        .state(State.Type.RUNNING)
                        .delayType(behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                            ExecutionDelay.DelayType.RESTART_FAILED_FLOW :
                            ExecutionDelay.DelayType.RESTART_FAILED_TASK);
                    executionDelays.add(executionDelayBuilder.build());
                    executor.withExecution(behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                            executionService.markWithTaskRunAs(executor.getExecution(), taskRun.getId(), State.Type.RETRIED, true) :
                            executionService.markWithTaskRunAs(executor.getExecution(), taskRun.getId(), State.Type.RETRYING, false),
                        "handleRetryTask");
                    // Prevent workerTaskResult of flowable to be sent
                    // because one of its children is retrying
                    if (taskRun.getParentTaskRunId() != null) {
                        list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId())).toList();
                    }
                }
            }
            // WaitFor case
            else if (task instanceof WaitFor waitFor && taskRun.getState().isRunning()) {
                if (waitFor.childTaskRunExecuted(executor.getExecution(), taskRun)) {
                    Output newOutput = waitFor.outputs(taskRun);
                    TaskRun updatedTaskRun = taskRun.withOutputs(newOutput.toMap());
                    RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution().withTaskRun(updatedTaskRun), updatedTaskRun);
                    List<NextTaskRun> next = ((FlowableTask<?>) task).resolveNexts(runContext, executor.getExecution(), updatedTaskRun);
                    Instant nextDate = waitFor.nextExecutionDate(runContext, executor.getExecution(), updatedTaskRun);
                    if (next.isEmpty()) {
                        return executor;
                    } else if (nextDate != null) {
                        executionDelays.add(ExecutionDelay.builder()
                            .taskRunId(taskRun.getId())
                            .executionId(executor.getExecution().getId())
                            .date(nextDate)
                            .state(State.Type.RUNNING)
                            .delayType(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)
                            .build());
                        Execution execution = executionService.pauseFlowable(executor.getExecution(), updatedTaskRun);
                        executor.withExecution(execution, "pauseLoop");
                    } else {
                        executor.withExecution(executor.getExecution().withTaskRun(updatedTaskRun), "handleWaitFor");
                    }
                }
            }

            // If the task is retrying
            // make sure that the workerTaskResult of the parent task is not sent
            if (taskRun.getState().isRetrying() && taskRun.getParentTaskRunId() != null) {
                list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId())).toList();
            }
        }

        executor.withWorkerTaskDelays(executionDelays, "handleChildWorkerTaskResult");

        if (list.isEmpty()) {
            return executor;
        }

        executor = this.handlePausedDelay(executor, list);

        return executor.withWorkerTaskResults(list, "handleChildWorkerTaskResult");
    }

    private Executor handlePausedDelay(Executor executor, List<WorkerTaskResult> workerTaskResults) throws InternalException {
        if (workerTaskResults
            .stream()
            .noneMatch(workerTaskResult -> workerTaskResult.getTaskRun().getState().getCurrent() == State.Type.PAUSED)) {
            return executor;
        }

        List<ExecutionDelay> list = workerTaskResults
            .stream()
            .filter(workerTaskResult -> workerTaskResult.getTaskRun().getState().getCurrent() == State.Type.PAUSED)
            .map(throwFunction(workerTaskResult -> {
                Task task = executor.getFlow().findTaskByTaskId(workerTaskResult.getTaskRun().getTaskId());

                if (task instanceof Pause pauseTask) {
                    if (pauseTask.getDelay() != null || pauseTask.getTimeout() != null) {
                        return ExecutionDelay.builder()
                            .taskRunId(workerTaskResult.getTaskRun().getId())
                            .executionId(executor.getExecution().getId())
                            .date(workerTaskResult.getTaskRun().getState().maxDate().plus(pauseTask.getDelay() != null ? pauseTask.getDelay() : pauseTask.getTimeout()))
                            .state(pauseTask.getDelay() != null ? State.Type.RUNNING : State.Type.FAILED)
                            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                            .build();
                    }
                }

                return null;
            }))
            .filter(Objects::nonNull)
            .toList();

        if (executor.getExecution().getState().getCurrent() != State.Type.PAUSED) {
            return executor
                .withExecution(executor.getExecution().withState(State.Type.PAUSED), "handlePausedDelay")
                .withWorkerTaskDelays(list, "handlePausedDelay");
        }

        return executor.withWorkerTaskDelays(list, "handlePausedDelay");
    }

    private Executor handleCreatedKilling(Executor executor) {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        List<WorkerTaskResult> workerTaskResults = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
            .map(t -> childWorkerTaskTypeToWorkerTask(
                Optional.of(State.Type.KILLED),
                t
            ))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        return executor.withWorkerTaskResults(workerTaskResults, "handleChildWorkerCreatedKilling");
    }

    private Executor handleListeners(Executor executor) {
        if (!executor.getExecution().getState().isTerminated()) {
            return executor;
        }

        List<ResolvedTask> currentTasks = conditionService.findValidListeners(executor.getFlow(), executor.getExecution());

        List<TaskRun> nexts = FlowableUtils.resolveSequentialNexts(executor.getExecution(), currentTasks)
            .stream()
            .map(throwFunction(NextTaskRun::getTaskRun))
            .toList();

        if (nexts.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(nexts, "handleListeners");
    }

    private Executor handleEnd(Executor executor) {
        if (executor.getExecution().getState().isTerminated() || executor.getExecution().getState().isPaused() || executor.getExecution().getState().isRetrying()) {
            return executor;
        }

        List<ResolvedTask> currentTasks = executor.getExecution().findTaskDependingFlowState(
            ResolvedTask.of(executor.getFlow().getTasks()),
            ResolvedTask.of(executor.getFlow().getErrors())
        );

        if (!executor.getExecution().isTerminated(currentTasks)) {
            return executor;
        }

        return this.onEnd(executor);
    }

    private Executor handleRestart(Executor executor) {
        if (executor.getExecution().getState().getCurrent() != State.Type.RESTARTED) {
            return executor;
        }

        metricRegistry
            .counter(MetricRegistry.EXECUTOR_EXECUTION_STARTED_COUNT, metricRegistry.tags(executor.getExecution()))
            .increment();

        logService.logExecution(
            executor.getExecution(),
            executor.getFlow().logger(),
            Level.INFO,
            "Flow restarted"
        );

        return executor.withExecution(executor.getExecution().withState(State.Type.RUNNING), "handleRestart");
    }

    private Executor handleKilling(Executor executor) {
        if (executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        Execution newExecution = executor.getExecution().withState(State.Type.KILLED);

        return executor.withExecution(newExecution, "handleKilling");
    }

    private Executor handleWorkerTask(final Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() == State.Type.KILLING) {
            return executor;
        }

        // submit TaskRun when receiving created, must be done after the state execution store
        Map<Boolean, List<WorkerTask>> workerTasks = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
            .map(throwFunction(taskRun -> {
                    Task task = executor.getFlow().findTaskByTaskId(taskRun.getTaskId());
                    RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun);
                    WorkerTask workerTask = WorkerTask.builder()
                        .runContext(runContext)
                        .taskRun(taskRun)
                        .task(task)
                        .build();
                    // Get worker group
                    String workerGroup = Optional.ofNullable(workerTask.getTask())
                        .map(Task::getWorkerGroup)
                        .map(WorkerGroup::getKey)
                        .orElse(null);
                    // Check if the worker group exist
                    String tenantId = executor.getFlow().getTenantId();
                    if (workerGroupExecutorInterface.isWorkerGroupExistForKey(workerGroup, tenantId)) {
                        // Check whether at-least one worker is available
                        if (workerGroupExecutorInterface.isWorkerGroupAvailableForKey(workerGroup)) {
                            return workerTask;
                        } else {
                            runContext.logger()
                                .error("Cannot run task. No workers are available for worker group '" + workerGroup + "'.");
                        }
                    } else {
                        runContext.logger()
                            .error("Cannot run task. No worker group exist for key '" + workerGroup + "'.");
                    }
                    // fail the task-run because no worker can run the task
                    return workerTask.withTaskRun(workerTask.getTaskRun().fail());
                })
            )
            .collect(Collectors.groupingBy(workerTask -> workerTask.getTaskRun().getState().isFailed()));

        if (workerTasks.isEmpty()) {
            return executor;
        }

        Executor executorToReturn = executor;

        // Handle WorkerTasks for FAILED TaskRun
        List<WorkerTask> workerTasksFailed = workerTasks.get(true);
        if (workerTasksFailed != null) {
            List<WorkerTaskResult> failed = workerTasksFailed
                .stream()
                .filter(workerTask -> workerTask.getTaskRun().getState().isFailed())
                .map(workerTask -> WorkerTaskResult.builder().taskRun(workerTask.getTaskRun()).build())
                .toList();
            executorToReturn = executorToReturn.withWorkerTaskResults(failed, "handleWorkerTask");
        }

        // Handle WorkerTasks for CREATED TaskRun
        List<WorkerTask> workerTasksCreated = workerTasks.get(false); // is not FAILED
        if (workerTasksCreated != null) {
            executorToReturn = executorToReturn.withWorkerTasks(workerTasksCreated, "handleWorkerTask");
        }

        return executorToReturn;
    }

    private Executor handleExecutableTask(final Executor executor) {
        List<SubflowExecution<?>> executions = new ArrayList<>();
        List<SubflowExecutionResult> subflowExecutionResults = new ArrayList<>();

        boolean haveFlows = executor.getWorkerTasks()
            .removeIf(workerTask -> {
                if (!(workerTask.getTask() instanceof ExecutableTask)) {
                    return false;
                }

                var executableTask = (Task & ExecutableTask<?>) workerTask.getTask();
                try {
                    // mark taskrun as running to avoid multiple try for failed
                    TaskRun executableTaskRun = executor.getExecution()
                        .findTaskRunByTaskRunId(workerTask.getTaskRun().getId());
                    executor.withExecution(
                        executor
                            .getExecution()
                            .withTaskRun(executableTaskRun.withState(State.Type.RUNNING)),
                        "handleExecutableTaskRunning"
                    );

                    RunContext runContext = runContextFactory.of(
                        executor.getFlow(),
                        executableTask,
                        executor.getExecution(),
                        executableTaskRun
                    );
                    List<SubflowExecution<?>> subflowExecutions = executableTask.createSubflowExecutions(runContext, flowExecutorInterface(), executor.getFlow(), executor.getExecution(), executableTaskRun);
                    if (subflowExecutions.isEmpty()) {
                        // if no executions we move the task to SUCCESS immediately
                        executor.withExecution(
                            executor
                                .getExecution()
                                .withTaskRun(executableTaskRun.withState(State.Type.SUCCESS)),
                            "handleExecutableTaskRunning.noExecution"
                        );
                    } else {
                        executions.addAll(subflowExecutions);
                        if (!executableTask.waitForExecution()) {
                            // send immediately all workerTaskResult to ends the executable task
                            for (SubflowExecution<?> subflowExecution : subflowExecutions) {
                                Optional<SubflowExecutionResult> subflowExecutionResult = executableTask.createSubflowExecutionResult(
                                    runContext,
                                    subflowExecution.getParentTaskRun().withState(State.Type.SUCCESS),
                                    executor.getFlow(),
                                    subflowExecution.getExecution()
                                );
                                subflowExecutionResult.ifPresent(subflowExecutionResults::add);
                            }
                        }
                    }
                } catch (Exception e) {
                    WorkerTaskResult failed = WorkerTaskResult.builder()
                        .taskRun(workerTask.getTaskRun().fail())
                        .build();
                    executor
                        .withWorkerTaskResults(List.of(failed), "handleExecutableTask")
                        .withException(e, "handleExecutableTask");
                }
                return true;
            });

        if (!haveFlows) {
            return executor;
        }

        Executor resultExecutor = executor.withSubflowExecutions(executions, "handleExecutableTask");

        if (!subflowExecutionResults.isEmpty()) {
            resultExecutor = executor.withSubflowExecutionResults(subflowExecutionResults, "handleExecutableTaskWorkerTaskResults");
        }

        return resultExecutor;
    }

    private Executor handleExecutionUpdatingTask(final Executor executor) {
        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();

        executor.getWorkerTasks()
            .removeIf(workerTask -> {
                if (!(workerTask.getTask() instanceof ExecutionUpdatableTask)) {
                    return false;
                }

                var executionUpdatingTask = (ExecutionUpdatableTask) workerTask.getTask();

                try {
                    executor.withExecution(
                        executionUpdatingTask.update(executor.getExecution(), workerTask.getRunContext())
                            .withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING)),
                        "handleExecutionUpdatingTask.updateExecution"
                    );

                    workerTaskResults.add(
                        WorkerTaskResult.builder()
                            .taskRun(workerTask.getTaskRun().withAttempts(
                                        Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build())
                                    )
                                    .withState(State.Type.SUCCESS)
                            )
                            .build()
                    );
                } catch (Exception e) {
                    workerTaskResults.add(WorkerTaskResult.builder()
                        .taskRun(workerTask.getTaskRun().fail())
                        .build());
                    executor.withException(e, "handleExecutionUpdatingTask");
                }
                return true;
            });

        if (!workerTaskResults.isEmpty()) {
            return executor.withWorkerTaskResults(workerTaskResults, "handleExecutionUpdatingTask.workerTaskResults");
        }

        return executor;
    }

    public Execution addDynamicTaskRun(Execution execution, Flow flow, WorkerTaskResult workerTaskResult) throws InternalException {
        ArrayList<TaskRun> taskRuns = new ArrayList<>(execution.getTaskRunList());

        // declared dynamic tasks
        if (!ListUtils.isEmpty(workerTaskResult.getDynamicTaskRuns())) {
            taskRuns.addAll(workerTaskResult.getDynamicTaskRuns());
        }

        // if parent, can be a Worker task that generate dynamic tasks
        if (workerTaskResult.getTaskRun().getParentTaskRunId() != null) {
            try {
                execution.findTaskRunByTaskRunId(workerTaskResult.getTaskRun().getId());
            } catch (InternalException e) {
                TaskRun parentTaskRun = execution.findTaskRunByTaskRunId(workerTaskResult.getTaskRun().getParentTaskRunId());
                Task parentTask = flow.findTaskByTaskId(parentTaskRun.getTaskId());

                if (parentTask instanceof WorkingDirectory) {
                    taskRuns.add(workerTaskResult.getTaskRun());
                }
            }
        }

        return taskRuns.size() > execution.getTaskRunList().size() ? execution.withTaskRunList(taskRuns) : null;
    }

    public boolean canBePurged(final Executor executor) {
        return executor.getExecution().isDeleted() || (
            executor.getFlow() != null &&
                // is terminated
                conditionService.isTerminatedWithListeners(executor.getFlow(), executor.getExecution())
                // we don't purge pause execution in order to be able to restart automatically in case of delay
                && executor.getExecution().getState().getCurrent() != State.Type.PAUSED
                // we don't purge killed execution in order to have feedback about child running tasks
                // this can be killed lately (after the executor kill the execution), but we want to keep
                // feedback about the actual state (killed or not)
                // @TODO: this can lead to infinite state store for most executor topic
                && executor.getExecution().getState().getCurrent() != State.Type.KILLED
        );
    }

    public void log(Logger log, Boolean in, WorkerJob value) {
        if (value instanceof WorkerTask workerTask) {
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                workerTask.getClass().getSimpleName(),
                workerTask.getTaskRun().toStringState()
            );
        } else if (value instanceof WorkerTrigger workerTrigger) {
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                workerTrigger.getClass().getSimpleName(),
                workerTrigger.getTriggerContext().uid()
            );
        }
    }

    public void log(Logger log, Boolean in, WorkerTaskResult value) {
        log.debug(
            "{} {} : {}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getTaskRun().toStringState()
        );
    }

    public void log(Logger log, Boolean in, SubflowExecutionResult value) {
        log.debug(
            "{} {} : {}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getParentTaskRun().toStringState()
        );
    }

    public void log(Logger log, Boolean in, Execution value) {
        log.debug(
            "{} {} [key='{}']\n{}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getId(),
            value.toStringState()
        );
    }

    public void log(Logger log, Boolean in, Executor value) {
        log.debug(
            "{} {} [key='{}', from='{}', offset='{}', crc32='{}']\n{}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getExecution().getId(),
            value.getFrom(),
            value.getOffset(),
            value.getExecution().toCrc32State(),
            value.getExecution().toStringState()
        );
    }

    public void log(Logger log, Boolean in, ExecutionKilledExecution value) {
        log.debug(
            "{} {} [key='{}']\n{}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getExecutionId(),
            value
        );
    }
}
