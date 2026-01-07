package io.kestra.executor;

import io.kestra.core.assets.AssetService;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.trace.propagation.RunContextTextMapSetter;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.Logs;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.plugin.core.flow.LoopUntil;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.ApplicationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
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
    private FlowInputOutput flowInputOutput;

    @Inject
    private WorkerGroupExecutorInterface workerGroupExecutorInterface;

    @Inject
    private WorkerJobRunningStateStore workerJobRunningStateStore;

    protected FlowMetaStoreInterface flowExecutorInterface;

    @Inject
    private ExecutionService executionService;

    @Inject
    private WorkerGroupService workerGroupService;

    @Inject
    private SLAService slaService;

    @Inject
    private Optional<OpenTelemetry> openTelemetry;

    @Inject
    private VariablesService variablesService;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private AssetService assetService;

    @Inject
    private RunContextInitializer runContextInitializer;

    protected FlowMetaStoreInterface flowExecutorInterface() {
        // bean is injected late, so we need to wait
        if (this.flowExecutorInterface == null) {
            this.flowExecutorInterface = applicationContext.getBean(FlowMetaStoreInterface.class);
        }

        return this.flowExecutorInterface;
    }

    public ExecutionRunning processExecutionRunning(FlowInterface flow, int runningCount, ExecutionRunning executionRunning) {
        // if concurrency was removed, it can be null as we always get the latest flow definition
        if (flow.getConcurrency() != null && runningCount >= flow.getConcurrency().getLimit()) {
            return switch (flow.getConcurrency().getBehavior()) {
                case QUEUE -> {
                    Logs.logExecution(
                        executionRunning.getExecution(),
                        Level.INFO,
                        "Execution is queued due to concurrency limit exceeded, {} running(s)",
                        runningCount
                    );
                    var newExecution = executionRunning.getExecution().withState(State.Type.QUEUED);
                    metricRegistry.counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT_DESCRIPTION, metricRegistry.tags(newExecution)).increment();
                    yield executionRunning
                        .withExecution(newExecution)
                        .withConcurrencyState(ExecutionRunning.ConcurrencyState.QUEUED);
                }
                case CANCEL ->
                    executionRunning
                        .withExecution(executionRunning.getExecution().withState(State.Type.CANCELLED))
                        .withConcurrencyState(ExecutionRunning.ConcurrencyState.CANCELLED);
                case FAIL -> {
                    var failedExecution = executionRunning.getExecution().failedExecutionFromExecutor(new IllegalStateException("Execution is FAILED due to concurrency limit exceeded"));
                    try {
                        logQueue.emitAsync(failedExecution.getLogs());
                    } catch (QueueException ex) {
                        // fail silently
                    }
                    yield executionRunning
                        .withExecution(failedExecution.getExecution())
                        .withConcurrencyState(ExecutionRunning.ConcurrencyState.FAILED);
                }

            };
        }

        // if under the limit, run it!
        return executionRunning
            .withExecution(executionRunning.getExecution().withState(State.Type.RUNNING))
            .withConcurrencyState(ExecutionRunning.ConcurrencyState.RUNNING);
    }

    public Executor process(Executor executor) {
        // previous failed (flow join can fail), just forward
        // or concurrency limit failed/cancelled the execution
        if (!executor.canBeProcessed() || executionService.isTerminated(executor.getFlow(), executor.getExecution())) {
            return executor;
        }

        long nanos = System.nanoTime();
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
            executor = this.handleAfterExecution(executor);

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
        } finally {
            metricRegistry
                .timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                .record(Duration.ofNanos(System.nanoTime() - nanos));
        }

        return executor;
    }

    public Execution onNexts(Execution execution, List<TaskRun> nexts) {
        if (log.isTraceEnabled()) {
            Logs.logExecution(
                execution,
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
                .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_STARTED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_STARTED_COUNT_DESCRIPTION, metricRegistry.tags(execution))
                .increment();

            Logs.logExecution(
                execution,
                Level.INFO,
                "Flow started"
            );

            newExecution = newExecution.withState(State.Type.RUNNING);
        }

        return newExecution;
    }

    private Optional<WorkerTaskResult> childWorkerTaskResult(FlowWithSource flow, Execution execution, TaskRun parentTaskRun) throws InternalException {
        Task parent = flow.findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask<?> flowableParent) {

            RunContext runContext = runContextFactory.of(flow, parent, execution, parentTaskRun);

            // first find the normal ended child tasks and send result
            Optional<State.Type> state;
            try {
                 state = flowableParent.resolveState(runContext, execution, parentTaskRun);
            } catch (Exception e) {
                // This will lead to the next task being still executed, but at least Kestra will not crash.
                // This is the best we can do, Flowable task should not fail, so it's a kind of panic mode.
                runContext.logger().error("Unable to resolve state from the Flowable task: {}", e.getMessage(), e);
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
                    Variables variables;
                    try {
                        // as flowable tasks can save outputs during iterative execution, we must merge the maps here
                        Output outputs = flowableParent.outputs(runContext);
                        Map<String, Object> outputMap = MapUtils.merge(workerTaskResult.getTaskRun().getOutputs(), outputs == null ? null : outputs.toMap());
                        variables = variablesService.of(StorageContext.forTask(workerTaskResult.getTaskRun()), outputMap);
                    } catch (Exception e) {
                        runContext.logger().error("Unable to resolve outputs from the Flowable task: {}", e.getMessage(), e);
                        variables = Variables.empty();
                    }

                    // flowable attempt state transition to terminated
                    List<TaskRunAttempt> attempts = Optional.ofNullable(parentTaskRun.getAttempts())
                        .map(ArrayList::new)
                        .orElseGet(ArrayList::new);
                    State.Type endedState = endedTask.get().getTaskRun().getState().getCurrent();
                    TaskRunAttempt updated = attempts.getLast().withState(endedState);
                    attempts.set( attempts.size() - 1, updated);

                    return Optional.of(new WorkerTaskResult(workerTaskResult
                        .getTaskRun()
                        .withOutputs(variables)
                        .withAttempts(attempts)
                    ));
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
                    FlowableUtils.resolveTasks(flowableParent.getErrors(), parentTaskRun),
                    FlowableUtils.resolveTasks(flowableParent.getFinally(), parentTaskRun)
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
            .map(throwFunction(type -> new WorkerTaskResult(taskRun.withState(type))));
    }

    private List<TaskRun> childNextsTaskRun(Executor executor, TaskRun parentTaskRun) throws InternalException {
        Task parent = executor.getFlow().findTaskByTaskId(parentTaskRun.getTaskId());
        if (parent instanceof FlowableTask<?> flowableParent) {
            // Count the number of flowable tasks executions, some flowable are being called multiple times,
            // so this is not exactly the number of flowable taskruns but the number of times they are executed.
            metricRegistry
                .counter(MetricRegistry.METRIC_EXECUTOR_FLOWABLE_EXECUTION_COUNT, MetricRegistry.METRIC_EXECUTOR_FLOWABLE_EXECUTION_COUNT_DESCRIPTION, metricRegistry.tags(parent))
                .increment();

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
                    return saveFlowableOutput(nexts, executor);
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
                RunContext runContext = runContextFactory.of(
                    executor.getFlow(),
                    t.getTask(),
                    executor.getExecution(),
                    t.getTaskRun()
                );

                try {
                    Output outputs = flowableTask.outputs(runContext);
                    Variables variables = variablesService.of(StorageContext.forTask(taskRun), outputs);
                    taskRun = taskRun.withOutputs(variables);

                } catch (Exception e) {
                    runContext.logger().warn("Unable to save output on taskRun '{}'", taskRun, e);
                }

                return taskRun;
            }))
            .toList();
    }

    private Executor onEnd(Executor executor) {
        final FlowWithSource flow = executor.getFlow();

        Execution newExecution = executor.getExecution()
            .withState(executor.getExecution().guessFinalState(flow));

        if (flow.getOutputs() != null) {
            RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
            var inputAndOutput = runContext.inputAndOutput();

            try {
                Map<String, Object> outputs = inputAndOutput.renderOutputs(flow.getOutputs());
                outputs = inputAndOutput.typedOutputs(flow, executor.getExecution(), outputs);
                newExecution = newExecution.withOutputs(outputs);
            } catch (Exception e) {
                Logs.logExecution(
                    executor.getExecution(),
                    Level.ERROR,
                    "Failed to render output values",
                    e
                );
                runContext.logger().error("Failed to render output values: {}", e.getMessage(), e);
                newExecution = newExecution.withState(State.Type.FAILED);
            }
        }

        Logs.logExecution(
            newExecution,
            Level.INFO,
            "Flow completed with state {} in {}",
            newExecution.getState().getCurrent(),
            newExecution.getState().humanDuration()
        );

        if (log.isTraceEnabled()) {
            log.trace(newExecution.toString(true));
        }

        metricRegistry
            .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_END_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_END_COUNT_DESCRIPTION, metricRegistry.tags(newExecution))
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DURATION_DESCRIPTION, metricRegistry.tags(newExecution))
            .record(newExecution.getState().getDurationOrComputeIt());

        return executor.withExecution(newExecution, "onEnd");
    }

    private Executor handleNext(Executor executor) {
        List<NextTaskRun> nextTaskRuns = FlowableUtils
            .resolveSequentialNexts(
                executor.getExecution(),
                ResolvedTask.of(executor.getFlow().getTasks()),
                ResolvedTask.of(executor.getFlow().getErrors()),
                ResolvedTask.of(executor.getFlow().getFinally())
            );

        if (nextTaskRuns.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(
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
        List<WorkerTask> onPauses = new ArrayList<>();

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
            /*
             * Check if the task is failed and if it has a retry policy
             */
            if (!executor.getExecution().getState().isRetrying() &&
                taskRun.getState().isFailed() &&
                (task instanceof RunnableTask<?> || task instanceof Subflow)
            ) {
                Instant nextRetryDate = null;
                AbstractRetry.Behavior behavior = null;

                // Case task has a retry
                if (task.getRetry() != null) {
                    AbstractRetry retry = task.getRetry();
                    behavior = retry.getBehavior();
                    nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                        taskRun.nextRetryDate(retry, executor.getExecution()) :
                        taskRun.nextRetryDate(retry);
                }
                else {
                    // Case parent task has a retry
                    AbstractRetry retry = searchForParentRetry(taskRun, executor);
                    if (retry != null) {
                        behavior = retry.getBehavior();
                        nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                            taskRun.nextRetryDate(retry, executor.getExecution()) :
                            taskRun.nextRetryDate(retry);
                    }
                    // Case flow has a retry
                    else if (executor.getFlow().getRetry() != null) {
                        retry = executor.getFlow().getRetry();
                        behavior = retry.getBehavior();
                        nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ?
                            executionService.nextRetryDate(retry, executor.getExecution()) :
                            taskRun.nextRetryDate(retry);
                    }
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
                        list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
                            .collect(Collectors.toCollection(ArrayList::new));
                    }
                }
            } else if (task instanceof LoopUntil waitFor && taskRun.getState().isRunning()) {
                if (waitFor.childTaskRunExecuted(executor.getExecution(), taskRun)) {
                    Output newOutput = waitFor.outputs(taskRun);
                    Variables variables = variablesService.of(StorageContext.forTask(taskRun), newOutput);
                    TaskRun updatedTaskRun = taskRun.withOutputs(variables);
                    RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution().withTaskRun(updatedTaskRun), updatedTaskRun);
                    List<NextTaskRun> next = ((FlowableTask<?>) task).resolveNexts(runContext, executor.getExecution(), updatedTaskRun);
                    Instant nextDate = waitFor.nextExecutionDate(runContext, executor.getExecution(), updatedTaskRun);
                     if (nextDate != null) {
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
            } else if (task instanceof Pause pause && pause.getOnPause() != null) {
                // if a Pause task defines an onPause, we must create a TaskRun and a WorkerTask
                RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                onPauses.add(WorkerTask.builder()
                    .runContext(runContext)
                    .taskRun(TaskRun.of(
                        executor.getExecution(),
                        ResolvedTask.of(pause.getOnPause())
                    ))
                    .task(pause.getOnPause())
                    .executionKind(executor.getExecution().getKind())
                    .build());
            }

            // If the task is retrying
            // make sure that the workerTaskResult of the parent task is not sent
            if (taskRun.getState().isRetrying() && taskRun.getParentTaskRunId() != null) {
                list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            }

            // If the task is a flowable and its terminated, check that all children are terminated.
            // This may not be the case for parallel flowable tasks like Parallel, Dag, ForEach...
            // After a fail task, some child flowable may not be correctly terminated.
            if (task instanceof FlowableTask<?> && taskRun.getState().isTerminated()) {
                List<TaskRun> updated = executor.getExecution().findChildren(taskRun).stream()
                    .filter(child -> !child.getState().isTerminated())
                    .map(throwFunction(child -> child.withState(taskRun.getState().getCurrent())))
                    .toList();
                if (!updated.isEmpty()) {
                    Execution execution = executor.getExecution();
                    for (TaskRun child : updated) {
                        execution = execution.withTaskRun(child);
                    }
                    executor = executor.withExecution(execution, "handledTerminatedFlowableTasks");
                }
            }
        }

        metricRegistry
            .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
            .increment(executionDelays.size());

        executor.withWorkerTaskDelays(executionDelays, "handleChildWorkerTaskDelay");

        if (list.isEmpty()) {
            return executor;
        }


        if (!onPauses.isEmpty()) {
            List<TaskRun> taskRuns = onPauses.stream().map(WorkerTask::getTaskRun).toList();
            executor.withTaskRun(taskRuns, "handlePauses");
            executor.withWorkerTasks(onPauses, "handlePauses");
        }

        executor = this.handlePausedDelay(executor, list);

        this.addWorkerTaskResults(executor, list);

        return executor;
    }

    private AbstractRetry searchForParentRetry(TaskRun taskRun, Executor executor) {
        // search in all parents, recursively
        if (taskRun.getParentTaskRunId() != null) {
            String taskId = taskRun.getTaskId();
            Task parentTask;
            do {
                parentTask = executor.getFlow().findParentTasksByTaskId(taskId);
                if (parentTask != null) {
                    taskId = parentTask.getId();
                }
            } while (parentTask != null && parentTask.getRetry() == null);

            if (parentTask != null) {
                return parentTask.getRetry();
            }
        }

        return null;
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
                    if (pauseTask.getPauseDuration() != null || pauseTask.getTimeout() != null) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        Duration duration = runContext.render(pauseTask.getPauseDuration()).as(Duration.class).orElse(null);
                        Duration timeout = runContext.render(pauseTask.getTimeout()).as(Duration.class).orElse(null);
                        Pause.Behavior behavior  = runContext.render(pauseTask.getBehavior()).as(Pause.Behavior.class).orElse(Pause.Behavior.RESUME);
                        if (duration != null || timeout != null) { // rendering can lead to null, so we must re-check here
                            // if duration is set, we use it, and we use the Pause behavior as a state
                            // if no duration, we use the standard timeout property and use FAILED as the target state
                            return ExecutionDelay.builder()
                                .taskRunId(workerTaskResult.getTaskRun().getId())
                                .executionId(executor.getExecution().getId())
                                .date(workerTaskResult.getTaskRun().getState().maxDate().plus(duration != null ? duration : timeout))
                                .state(duration != null ? behavior.mapToState() : State.Type.fail(pauseTask))
                                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                                .build();
                        }
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

    private Executor handleCreatedKilling(Executor executor) throws InternalException {
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

        this.addWorkerTaskResults(executor, workerTaskResults);
        return executor;
    }

    private Executor handleAfterExecution(Executor executor) {
        if (!executor.getExecution().getState().isTerminated()) {
            return executor;
        }

        // first, execute listeners
        List<ResolvedTask> listenerResolvedTasks = conditionService.findValidListeners(executor.getFlow(), executor.getExecution());
        List<TaskRun> listenerNexts = FlowableUtils.resolveSequentialNexts(executor.getExecution(), listenerResolvedTasks)
            .stream()
            .map(throwFunction(NextTaskRun::getTaskRun))
            .toList();

        if (!listenerNexts.isEmpty()) {
            return executor.withTaskRun(listenerNexts, "handleListeners");
        }

        // then, check if all listener tasks are terminated
        if (!listenerResolvedTasks.isEmpty() && !executor.getExecution().isTerminated(listenerResolvedTasks)) {
            return executor;
        }

        // then, when no more listeners, execute afterExecution tasks
        List<ResolvedTask> afterExecutionResolvedTasks = executionService.resolveAfterExecutionTasks(executor.getFlow());
        List<TaskRun> afterExecutionNexts = FlowableUtils.resolveSequentialNexts(executor.getExecution(), afterExecutionResolvedTasks)
            .stream()
            .map(throwFunction(NextTaskRun::getTaskRun))
            .map(taskRun -> taskRun.withForceExecution(true)) // forceExecution so it would be executed even if the execution is killed
            .toList();
        if (!afterExecutionNexts.isEmpty()) {
            return executor.withTaskRun(afterExecutionNexts, "handleAfterExecution ");
        }

        // if nothing more, just return the executor as is
        return executor;
    }

    private Executor handleEnd(Executor executor) {
        if (executor.getExecution().getState().isTerminated() || executor.getExecution().getState().isPaused() || executor.getExecution().getState().isRetrying()) {
            return executor;
        }

        List<ResolvedTask> currentTasks = executor.getExecution().findTaskDependingFlowState(
            ResolvedTask.of(executor.getFlow().getTasks()),
            ResolvedTask.of(executor.getFlow().getErrors()),
            ResolvedTask.of(executor.getFlow().getFinally())
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
            .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_STARTED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_STARTED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
            .increment();

        Logs.logExecution(
            executor.getExecution(),
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

        Optional<TextMapPropagator> textMapPropagator = openTelemetry
            .map(OpenTelemetry::getPropagators)
            .map(ContextPropagators::getTextMapPropagator);

        // submit TaskRun when receiving created, must be done after the state execution store
        Map<Boolean, List<WorkerTask>> workerTasks = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated() && executor.getExecution().getFixtureForTaskRun(taskRun).isEmpty())
            .map(throwFunction(taskRun -> {
                    Task task = executor.getFlow().findTaskByTaskId(taskRun.getTaskId());
                    RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun);

                    // inject the traceparent into the run context
                    textMapPropagator.ifPresent(propagator -> propagator.inject(Context.current(), runContext, RunContextTextMapSetter.INSTANCE));

                    WorkerTask workerTask = WorkerTask.builder()
                        .runContext(runContext)
                        .taskRun(taskRun)
                        .task(task)
                        .executionKind(executor.getExecution().getKind())
                        .build();
                    // Get worker group
                    Optional<WorkerGroup> workerGroup = workerGroupService.resolveGroupFromJob(executor.getFlow(), workerTask);
                    if (workerGroup.isPresent()) {
                        // Check if the worker group exist
                        String tenantId = executor.getFlow().getTenantId();
                        String workerGroupKey = runContext.render(workerGroup.get().getKey());
                        if (workerGroupExecutorInterface.isWorkerGroupExistForKey(workerGroupKey, tenantId)) {
                            // Check whether at-least one worker is available
                            if (workerGroupExecutorInterface.isWorkerGroupAvailableForKey(workerGroupKey)) {
                                return workerTask;
                            } else {
                                WorkerGroup.Fallback fallback = workerGroup.map(wg -> wg.getFallback()).orElse(WorkerGroup.Fallback.WAIT);
                                return switch(fallback) {
                                    case FAIL -> {
                                        runContext.logger()
                                            .error("No workers are available for worker group '{}', failing the task.", workerGroupKey);
                                        yield workerTask.withTaskRun(workerTask.getTaskRun().fail());
                                    }
                                    case CANCEL -> {
                                        runContext.logger()
                                            .info("No workers are available for worker group '{}', canceling the task.", workerGroupKey);
                                        yield workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.CANCELLED));
                                    }
                                    case WAIT -> {
                                        runContext.logger()
                                            .info("No workers are available for worker group '{}', waiting for one to be available.", workerGroupKey);
                                        yield workerTask;
                                    }
                                };
                            }
                        } else {
                            runContext.logger()
                                .error("Cannot run task. No worker group exist for key '{}'.", workerGroupKey);
                            // fail the task-run because no worker can run the task
                            return workerTask.withTaskRun(workerTask.getTaskRun().fail());
                        }
                    } else {
                        return workerTask;
                    }
                })
            )
            .collect(Collectors.groupingBy(workerTask -> workerTask.getTaskRun().getState().isFailed() || workerTask.getTaskRun().getState().getCurrent() == State.Type.CANCELLED));

        // mock WorkerTaskResult for mocked execution
        // submit TaskRun when receiving created, must be done after the state execution store
        boolean hasMockedWorkerTask = false;
        record FixtureAndTaskRun(TaskFixture fixture, TaskRun taskRun) {}
        if (executor.getExecution().getFixtures() != null) {
            RunContext runContext = runContextInitializer.forExecutor((DefaultRunContext) runContextFactory.of(
                executor.getFlow(),
                executor.getExecution()
            ));
            List<WorkerTaskResult> workerTaskResults = executor.getExecution()
                .getTaskRunList()
                .stream()
                .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
                .flatMap(taskRun -> executor.getExecution().getFixtureForTaskRun(taskRun).stream().map(fixture -> new FixtureAndTaskRun(fixture, taskRun)))
                .map(throwFunction(fixtureAndTaskRun -> {
                    Optional<AssetsDeclaration> renderedAssetsDeclaration = runContext.render(executor.getFlow().findTaskByTaskId(fixtureAndTaskRun.taskRun.getTaskId()).getAssets()).as(AssetsDeclaration.class);
                        return WorkerTaskResult.builder()
                            .taskRun(fixtureAndTaskRun.taskRun()
                                .withState(Optional.ofNullable(fixtureAndTaskRun.fixture().getState()).orElse(State.Type.SUCCESS))
                                .withOutputs(
                                    variablesService.of(StorageContext.forTask(fixtureAndTaskRun.taskRun),
                                        fixtureAndTaskRun.fixture().getOutputs() == null ? null : runContext.render(fixtureAndTaskRun.fixture().getOutputs()))
                                )
                                .withAssets(new AssetsInOut(
                                    renderedAssetsDeclaration.map(AssetsDeclaration::getInputs).orElse(Collections.emptyList()).stream()
                                        .map(assetIdentifier -> assetIdentifier.withTenantId(executor.getFlow().getTenantId()))
                                        .toList(),
                                    fixtureAndTaskRun.fixture().getAssets() == null ? null : fixtureAndTaskRun.fixture().getAssets().stream()
                                        .map(asset -> asset.withTenantId(executor.getFlow().getTenantId()))
                                        .toList()
                                ))
                            )
                            .build();
                    }
                ))
                .toList();

            hasMockedWorkerTask = !workerTaskResults.isEmpty();
            this.addWorkerTaskResults(executor, workerTaskResults);
        }

        if (workerTasks.isEmpty() || hasMockedWorkerTask) {
            return executor;
        }

        Executor executorToReturn = executor;

        // suspend on breakpoint: if a breakpoint is for a CREATED taskrun, set the execution state to BREAKPOINT and ends here
        if (!ListUtils.isEmpty(executor.getExecution().getBreakpoints())) {
            List<Breakpoint> breakpoints = executor.getExecution().getBreakpoints();
            if (executor.getExecution()
                .getTaskRunList()
                .stream()
                .anyMatch(taskRun -> shouldSuspend(taskRun, breakpoints))
            ) {
                List<TaskRun> newTaskRuns = executor.getExecution().getTaskRunList().stream().map(
                    taskRun -> {
                        if (shouldSuspend(taskRun, breakpoints)) {
                            return taskRun.withState(State.Type.BREAKPOINT);
                        }
                        return taskRun;
                    }
                ).toList();
                Execution newExecution = executor.getExecution().withTaskRunList(newTaskRuns).withState(State.Type.BREAKPOINT);
                executorToReturn = executorToReturn.withExecution(newExecution, "handleBreakpoint");
                Logs.logExecution(
                    newExecution,
                    Level.INFO,
                    "Flow is suspended at a breakpoint."
                );
            }
        }

        // Ends FAILED or CANCELLED task runs by creating worker task results
        List<WorkerTask> endedTasks = workerTasks.get(true);
        if (endedTasks != null && !endedTasks.isEmpty()) {
            List<WorkerTaskResult> failed = endedTasks
                .stream()
                .map(workerTask -> WorkerTaskResult.builder().taskRun(workerTask.getTaskRun()).build())
                .toList();

            this.addWorkerTaskResults(executor, failed);
        }

        // Send other TaskRun to the worker (create worker tasks)
        List<WorkerTask> processingTasks = workerTasks.get(false);
        if (processingTasks != null && !processingTasks.isEmpty() && !executor.getExecution().getState().isBreakpoint()) {
            executorToReturn = executorToReturn.withWorkerTasks(processingTasks, "handleWorkerTask");

            metricRegistry.counter(MetricRegistry.METRIC_EXECUTOR_TASKRUN_CREATED_COUNT, MetricRegistry.METRIC_EXECUTOR_TASKRUN_CREATED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution())).increment(processingTasks.size());
        }

        return executorToReturn;
    }

    private boolean shouldSuspend(TaskRun taskRun, List<Breakpoint> breakpoints) {
        return taskRun.getState().getCurrent().isCreated() && breakpoints.stream()
                .anyMatch(breakpoint -> taskRun.getTaskId().equals(breakpoint.getId()) && (breakpoint.getValue() == null || Objects.equals(taskRun.getValue(), breakpoint.getValue())));
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

                    // handle runIf
                    if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                        executor.withExecution(
                            executor
                                .getExecution()
                                .withTaskRun(executableTaskRun.withState(State.Type.SKIPPED).addAttempt(TaskRunAttempt.builder().state(new State().withState(State.Type.SKIPPED)).build())),
                            "handleExecutableTaskSkipped"
                        );
                        return false;
                    }

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
                        Optional<FlowInterface> flow = flowExecutorInterface.findByExecution(subflowExecutions.getFirst().getExecution());
                        if (flow.isPresent()) {
                            // add SubflowExecutionResults to notify parents
                            for (SubflowExecution<?> subflowExecution : subflowExecutions) {
                                Optional<SubflowExecutionResult> subflowExecutionResult = executableTask.createSubflowExecutionResult(
                                    runContext,
                                    // if we didn't wait for the execution, we directly set the state to SUCCESS
                                    executableTask.waitForExecution() ? subflowExecution.getParentTaskRun() : subflowExecution.getParentTaskRun().withState(State.Type.SUCCESS),
                                    flow.get(),
                                    subflowExecution.getExecution()
                                );
                                subflowExecutionResult.ifPresent(subflowExecutionResults::add);
                            }
                        } else {
                            log.error("Unable to find flow for execution {}", subflowExecutions.getFirst().getExecution().getId());
                        }
                    }
                } catch (Exception e) {
                    try {
                        executor
                            .withExecution(executor.getExecution().withTaskRun(workerTask.getTaskRun().fail()), "handleExecutableTask")
                            .withException(e, "handleExecutableTask");
                    } catch (InternalException ex) {
                        log.error("Unable to fail the executable task.", ex);
                    }
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

    private Executor handleExecutionUpdatingTask(final Executor executor) throws InternalException {
        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();

        executor.getWorkerTasks()
            .removeIf(workerTask -> {
                if (!(workerTask.getTask() instanceof ExecutionUpdatableTask executionUpdatingTask)) {
                    return false;
                }

                try {
                    // Skip task if runIf condition is false
                    if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                        executor.withExecution(
                            executor
                                .getExecution()
                                .withTaskRun(workerTask.getTaskRun().withState(State.Type.SKIPPED).addAttempt(TaskRunAttempt.builder().state(new State().withState(State.Type.SKIPPED)).build())),
                            "handleExecutionUpdatingTaskSkipped"
                        );
                        return false;
                    }

                    TaskRun runningTaskRun = workerTask
                        .getTaskRun()
                        .withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build()))
                        .withState(State.Type.RUNNING);

                    executor.withExecution(
                        executionUpdatingTask.update(executor.getExecution(), workerTask.getRunContext())
                            .withTaskRun(runningTaskRun),
                        "handleExecutionUpdatingTask.updateExecution"
                    );

                    var terminalState = executionUpdatingTask
                        .resolveState(workerTask.getRunContext(), executor.getExecution())
                        .orElse(State.Type.SUCCESS);

                    TaskRunAttempt terminalAttempt = runningTaskRun.lastAttempt().withState(terminalState);

                    workerTaskResults.add(
                        WorkerTaskResult.builder()
                            .taskRun(runningTaskRun
                                .withAttempts(List.of(terminalAttempt))
                                .withState(terminalState)
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

        this.addWorkerTaskResults(executor, workerTaskResults);

        return executor;
    }

    public void addWorkerTaskResults(Executor executor, List<WorkerTaskResult> workerTaskResults) throws InternalException {
        for (WorkerTaskResult workerTaskResult : workerTaskResults) {
            this.addWorkerTaskResult(executor, () -> executor.getFlow(), workerTaskResult);
        }
    }

    public void addWorkerTaskResult(Executor executor, Supplier<FlowWithSource> flow, WorkerTaskResult workerTaskResult) throws InternalException {
        // dynamic tasks
        Execution newExecution = this.addDynamicTaskRun(
            executor.getExecution(),
            flow,
            workerTaskResult
        );
        if (newExecution != null) {
            executor.withExecution(newExecution, "addDynamicTaskRun");
        }

        TaskRun taskRun = workerTaskResult.getTaskRun();
        newExecution = executor.getExecution().withTaskRun(taskRun);
        // If the worker task result is killed, we must check if it has a parents to also kill them if not already done.
        // Running flowable tasks that have child tasks running in the worker will be killed thanks to that.
        if (taskRun.getState().getCurrent() == State.Type.KILLED && taskRun.getParentTaskRunId() != null) {
            newExecution = executionService.killParentTaskruns(taskRun, newExecution);
        }
        executor.withExecution(newExecution, "addWorkerTaskResult");
        if (taskRun.getState().isTerminated()) {
            log.trace("TaskRun terminated: {}", taskRun);
            workerJobRunningStateStore.deleteByKey(taskRun.getId());
            metricRegistry
                .counter(
                    MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT,
                    MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT_DESCRIPTION,
                    metricRegistry.tags(workerTaskResult)
                )
                .increment();

            metricRegistry
                .timer(
                    MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION,
                    MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION_DESCRIPTION,
                    metricRegistry.tags(workerTaskResult)
                )
                .record(taskRun.getState().getDurationOrComputeIt());

            if (
                taskRun.getAssets() != null &&
                    (!taskRun.getAssets().getInputs().isEmpty() || !taskRun.getAssets().getOutputs().isEmpty())
            ) {
                AssetUser assetUser = new AssetUser(
                    taskRun.getTenantId(),
                    taskRun.getNamespace(),
                    taskRun.getFlowId(),
                    newExecution.getFlowRevision(),
                    taskRun.getExecutionId(),
                    taskRun.getTaskId(),
                    taskRun.getId(),
                    taskRun.getState().getCurrent(),
                    taskRun.getState().getStartDate(),
                    taskRun.getState().getEndDate().orElse(null)
                );

                List<AssetIdentifier> outputIdentifiers = taskRun.getAssets().getOutputs().stream()
                    .map(asset -> asset.withTenantId(taskRun.getTenantId()))
                    .map(AssetIdentifier::of)
                    .toList();
                List<AssetIdentifier> inputAssets = taskRun.getAssets().getInputs().stream()
                    .map(assetIdentifier -> assetIdentifier.withTenantId(taskRun.getTenantId()))
                    .toList();
                try {
                    assetService.assetLineage(
                        assetUser,
                        inputAssets,
                        outputIdentifiers
                    );
                } catch (QueueException e) {
                    log.warn("Unable to submit asset lineage event for {} -> {}", inputAssets, outputIdentifiers, e);
                }

                // don't update output asserts if task fail
                if (!taskRun.getState().isFailed()) {
                    taskRun.getAssets().getOutputs().forEach(asset -> {
                        try {
                            assetService.asyncUpsert(assetUser, asset);
                        } catch (QueueException e) {
                            log.warn("Unable to submit asset upsert event for asset {}", asset.getId(), e);
                        }
                    });
                }
            }
        }
    }

    // Note: as the flow is only used in an error branch and it can take time to load, we pass it thought a Supplier
    private Execution addDynamicTaskRun(Execution execution, Supplier<FlowWithSource> flow, WorkerTaskResult workerTaskResult) throws InternalException {
        ArrayList<TaskRun> taskRuns = new ArrayList<>(ListUtils.emptyOnNull(execution.getTaskRunList()));

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
                Task parentTask = flow.get().findTaskByTaskId(parentTaskRun.getTaskId());

                if (parentTask instanceof WorkingDirectory) {
                    taskRuns.add(workerTaskResult.getTaskRun());
                }
            }
        }

        return taskRuns.size() > ListUtils.emptyOnNull(execution.getTaskRunList()).size() ? execution.withTaskRunList(taskRuns) : null;
    }

    public boolean canBePurged(final Executor executor) {
        return executor.getExecution().isDeleted() || (
            executor.getFlow() != null &&
                // is terminated
                executionService.isTerminated(executor.getFlow(), executor.getExecution())
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
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
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
    }

    public void log(Logger log, Boolean in, WorkerTaskResult value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getTaskRun().toStringState()
            );
        }
    }

    public void log(Logger log, Boolean in, SubflowExecutionResult value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getParentTaskRun().toStringState()
            );
        }
    }

    public void log(Logger log, Boolean in, SubflowExecutionEnd value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.toStringState()
            );
        }
    }

    public void log(Logger log, Boolean in, Execution value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} [key='{}']\n{}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getId(),
                value.toStringState()
            );
        }
    }

    public void log(Logger log, Boolean in, Executor value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
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

    /**
     * Handle flow ExecutionChangedSLA on an executor.
     * If there are SLA violations, it will take care of updating the execution based on the SLA behavior.
     * @see #processViolation(RunContext, Executor, Violation)
     * <p>
     * WARNING: ATM, only the first violation will update the execution.
     */
    public Executor handleExecutionChangedSLA(Executor executor) throws QueueException {
        if (executor.getFlow() == null || ListUtils.isEmpty(executor.getFlow().getSla()) || executor.getExecution().getState().isTerminated()) {
            return executor;
        }

        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
        List<Violation> violations = slaService.evaluateExecutionChangedSLA(runContext, executor.getFlow(), executor.getExecution());
        if (!violations.isEmpty()) {
            metricRegistry
                .counter(MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                .increment(violations.size());

            // For now, we only consider the first violation to be capable of updating the execution.
            // Other violations would only be logged.
            Violation violation = violations.getFirst();
            return processViolation(runContext, executor, violation);
        }

        return executor;
    }

    /**
     * Process an SLA violation on an executor:
     * - If behavior is FAIL or CANCEL: kill the execution, then return it with the new state.
     * - If behavior is NONE: do nothing and return an unmodified executor.
     * <p>
     * Then, if there are labels, they are added to the SLA (modifying the executor)
     */
    public Executor processViolation(RunContext runContext, Executor executor, Violation violation) throws QueueException {
        boolean hasChanged = false;
        Execution newExecution = switch (violation.behavior()) {
            case FAIL -> {
                runContext.logger().error("Execution failed due to SLA '{}' violated: {}", violation.slaId(), violation.reason());
                hasChanged = true;
                yield markAs(executor.getExecution(), State.Type.FAILED);
            }
            case CANCEL -> {
                hasChanged = true;
                yield markAs(executor.getExecution(), State.Type.CANCELLED);
            }
            case NONE -> executor.getExecution();
        };

        if (!ListUtils.isEmpty(violation.labels()) && !LabelService.containsAll(executor.getExecution().getLabels(), violation.labels())) {
            List<Label> labels = new ArrayList<>(newExecution.getLabels());
            labels.addAll(violation.labels());
            hasChanged = true;
            newExecution = newExecution.withLabels(labels);
        }

        if (hasChanged) {
            return executor.withExecution(newExecution, "SLAViolation");
        }
        return executor;
    }

    private Execution markAs(Execution execution, State.Type state) throws QueueException {
        Execution newExecution = execution.findLastNotTerminated()
            .map(taskRun -> {
                try {
                    return execution.withTaskRun(taskRun.withState(state));
                } catch (InternalException e) {
                    // in case we cannot update the last not terminated task run, we ignore it
                    return execution;
                }
            })
            .orElse(execution)
            .withState(state);

        killQueue.emit(ExecutionKilledExecution
            .builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionState(state)
            .executionId(execution.getId())
            .isOnKillCascade(true)
            .tenantId(execution.getTenantId())
            .build()
        );

        return newExecution;
    }
}
