package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.utils.*;
import io.kestra.plugin.core.flow.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.kestra.core.assets.AssetService;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.WorkerJobRunningStateStore;
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
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.services.*;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.trace.propagation.RunContextTextMapSetter;

import io.micronaut.context.ApplicationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class ExecutorService {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private WorkerGroupMetaStore workerGroupMetaStore;

    @Inject
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
    protected BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private AssetService assetService;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private TaskOutputService taskOutputService;

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
                    metricRegistry
                        .counter(MetricRegistry.METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT_DESCRIPTION, metricRegistry.tags(newExecution))
                        .increment();
                    yield executionRunning
                        .withExecution(newExecution)
                        .withConcurrencyState(ExecutionRunning.ConcurrencyState.QUEUED);
                }
                case CANCEL -> executionRunning
                    .withExecution(executionRunning.getExecution().withState(State.Type.CANCELLED))
                    .withConcurrencyState(ExecutionRunning.ConcurrencyState.CANCELLED);
                case FAIL -> {
                    var failedExecution = executionRunning.getExecution().failedExecutionFromExecutor(new IllegalStateException("Execution is FAILED due to concurrency limit exceeded"));
                    var logger = runContextLoggerFactory.create(executionRunning.getExecution());
                    logger.emitLogs(failedExecution.logs());
                    yield executionRunning
                        .withExecution(failedExecution.execution())
                        .withConcurrencyState(ExecutionRunning.ConcurrencyState.FAILED);
                }

            };
        }

        // if under the limit, run it!
        return executionRunning
            .withExecution(executionRunning.getExecution().withState(State.Type.RUNNING))
            .withConcurrencyState(ExecutionRunning.ConcurrencyState.RUNNING);
    }

    public ExecutorContext process(ExecutorContext executor) {
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

            // process next task if not killing, killed or queued
            if (
                executor.getExecution().getState().getCurrent() != State.Type.KILLING && executor.getExecution().getState().getCurrent() != State.Type.KILLED
                    && executor.getExecution().getState().getCurrent() != State.Type.QUEUED
            ) {
                executor = this.handleNext(executor);
                executor = this.handleFlowableTasks(executor);
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
                .timer(
                    MetricRegistry.METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION, MetricRegistry.METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION_DESCRIPTION,
                    metricRegistry.tags(executor.getExecution())
                )
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

    public ExecutorContext handleFailedExecutionFromExecutor(ExecutorContext executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);
        var logger = runContextLoggerFactory.create(failedExecutionWithLog.execution());
        logger.emitLogs(failedExecutionWithLog.logs());
        return executor.withExecution(failedExecutionWithLog.execution(), "exception");
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
                    var outputs = workerTaskResult.getOutputs();
                    try {
                        // as flowable tasks can save outputs during iterative execution, we must merge the maps here
                        Output parentOutputs = flowableParent.outputs(runContext);
                        if (parentOutputs != null) {
                            outputs = MapUtils.merge(outputs, parentOutputs.toMap());
                        }
                    } catch (Exception e) {
                        runContext.logger().error("Unable to resolve outputs from the Flowable task: {}", e.getMessage(), e);
                        outputs = Collections.emptyMap();
                    }

                    taskOutputService.saveOutputs(workerTaskResult.getTaskRun(), outputs);

                    // flowable attempt state transition to terminated
                    List<TaskRunAttempt> attempts = Optional.ofNullable(parentTaskRun.getAttempts())
                        .map(ArrayList::new)
                        .orElseGet(ArrayList::new);
                    if (!attempts.isEmpty()) { // can occur on migration from pre-1.2
                        State.Type endedState = endedTask.get().getTaskRun().getState().getCurrent();
                        TaskRunAttempt updated = attempts.getLast().withState(endedState);
                        attempts.set(attempts.size() - 1, updated);
                    }

                    return Optional.of(
                        new WorkerTaskResult(
                            workerTaskResult
                                .getTaskRun()
                                .withAttempts(attempts)
                        )
                    );
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
        TaskRun taskRun) {
        return findState
            .map(throwFunction(type -> new WorkerTaskResult(taskRun.withState(type))));
    }

    private List<TaskRun> childNextsTaskRun(ExecutorContext executor, TaskRun parentTaskRun) throws InternalException {
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
        ExecutorContext executor) {
        return nextTaskRuns
            .stream()
            .map(throwFunction(t ->
            {
                TaskRun taskRun = t.getTaskRun();

                if (!(t.getTask() instanceof FlowableTask<?> flowableTask)) {
                    return taskRun;
                }
                RunContext runContext = runContextFactory.of(
                    executor.getFlow(),
                    t.getTask(),
                    executor.getExecution(),
                    t.getTaskRun()
                );

                try {
                    Output outputs = flowableTask.outputs(runContext);
                    taskOutputService.saveOutputs(taskRun, outputs);

                } catch (Exception e) {
                    runContext.logger().warn("Unable to save output on taskRun '{}'", taskRun, e);
                }

                return taskRun;
            }))
            .toList();
    }

    private ExecutorContext onEnd(ExecutorContext executor) {
        final FlowWithSource flow = executor.getFlow();

        // For LOOP sub-executions, use guessFinalState(List<ResolvedTask>, TaskRun, boolean, boolean) to compute the final state from the Loop's child tasks directly.
        State.Type finalState;
        if (executor.getExecution().getKind() == ExecutionKind.LOOP && executor.getExecution().getLoopRun() != null) {
            Loop loop = (Loop) flow.findTaskByTaskIdOrNull(executor.getExecution().getLoopRun().taskId());
            if (loop != null) {
                List<ResolvedTask> childTasks = loop.getTasks().stream().map(ResolvedTask::of).toList();
                finalState = executor.getExecution().guessFinalState(childTasks, null, false, false);
            } else {
                finalState = executor.getExecution().guessFinalState(flow);
            }
        } else {
            finalState = executor.getExecution().guessFinalState(flow);
        }

        Execution newExecution = executor.getExecution()
            .withState(finalState);

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

    private ExecutorContext handleNext(ExecutorContext executor) throws InternalException {
        List<NextTaskRun> nextTaskRuns;
        if (executor.getExecution().getKind() != ExecutionKind.LOOP) {
            nextTaskRuns = FlowableUtils.resolveSequentialNexts(
                    executor.getExecution(),
                    ResolvedTask.of(executor.getFlow().getTasks()),
                    ResolvedTask.of(executor.getFlow().getErrors()),
                    ResolvedTask.of(executor.getFlow().getFinally())
                );
        } else if (executor.getExecution().getLoopRun() != null) { // should always be true but better be safe
            // for LOOP executions: we only execute the loop itself, not the whole execution
            Loop loop = (Loop) executor.getFlow().findTaskByTaskId(executor.getExecution().getLoopRun().taskId());
            // Build a minimal task run representing the Loop in the parent execution so that child task runs get parentTaskRunId set.
            TaskRun loopTaskRun = TaskRun.builder()
                .id(executor.getExecution().getLoopRun().taskRunId())
                .build();
            nextTaskRuns = FlowableUtils.resolveSequentialNexts(
                executor.getExecution(),
                FlowableUtils.resolveTasks(loop.getTasks(), loopTaskRun),
                FlowableUtils.resolveTasks(loop.getErrors(), loopTaskRun),
                FlowableUtils.resolveTasks(loop.getFinally(), loopTaskRun),
                loopTaskRun
            );
        } else {
            // should never happen but better be safe
            return executor;
        }

        if (nextTaskRuns.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(
            this.saveFlowableOutput(nextTaskRuns, executor),
            "handleNext"
        );
    }

    private ExecutorContext handleFlowableTasks(ExecutorContext executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<TaskRun> running = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .toList();

        // Remove functional style to avoid (class io.kestra.core.exceptions.IllegalVariableEvaluationException cannot be cast to class java.lang.RuntimeException'
        List<TaskRun> result = new ArrayList<>();

        for (TaskRun taskRun : running) {
            result.addAll(this.childNextsTaskRun(executor, taskRun));
        }

        if (result.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(result, "handleChildNext");
    }

    private ExecutorContext handleChildWorkerTaskResult(ExecutorContext executor) throws Exception {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<WorkerTaskResult> list = new ArrayList<>();
        List<ExecutionDelay> executionDelays = new ArrayList<>();
        List<ExecutorContext.ExecutorWorkerTask> onPauses = new ArrayList<>();

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
            if (
                !executor.getExecution().getState().isRetrying() &&
                    taskRun.getState().isFailed() &&
                    (task instanceof RunnableTask<?> || task instanceof Subflow)
            ) {
                Instant nextRetryDate = null;
                AbstractRetry.Behavior behavior = null;

                // Case task has a retry
                if (task.getRetry() != null) {
                    AbstractRetry retry = task.getRetry();
                    behavior = retry.getBehavior();
                    nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ? taskRun.nextRetryDate(retry, executor.getExecution()) : taskRun.nextRetryDate(retry);
                } else {
                    // Case parent task has a retry
                    AbstractRetry retry = searchForParentRetry(taskRun, executor);
                    if (retry != null) {
                        behavior = retry.getBehavior();
                        nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ? taskRun.nextRetryDate(retry, executor.getExecution()) : taskRun.nextRetryDate(retry);
                    }
                    // Case flow has a retry
                    else if (executor.getFlow().getRetry() != null) {
                        retry = executor.getFlow().getRetry();
                        behavior = retry.getBehavior();
                        nextRetryDate = behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ? executionService.nextRetryDate(retry, executor.getExecution())
                            : taskRun.nextRetryDate(retry);
                    }
                }

                if (nextRetryDate != null) {
                    ExecutionDelay.ExecutionDelayBuilder executionDelayBuilder = ExecutionDelay.builder()
                        .taskRunId(taskRun.getId())
                        .executionId(executor.getExecution().getId())
                        .date(nextRetryDate)
                        .state(State.Type.RUNNING)
                        .delayType(behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ? ExecutionDelay.DelayType.RESTART_FAILED_FLOW : ExecutionDelay.DelayType.RESTART_FAILED_TASK);
                    executionDelays.add(executionDelayBuilder.build());
                    executor.withExecution(
                        behavior.equals(AbstractRetry.Behavior.CREATE_NEW_EXECUTION) ? executionService.markWithTaskRunAs(executor.getExecution(), taskRun.getId(), State.Type.RETRIED, true)
                            : executionService.markWithTaskRunAs(executor.getExecution(), taskRun.getId(), State.Type.RETRYING, false),
                        "handleRetryTask"
                    );
                    // Prevent workerTaskResult from flowable tasks to be sent because one of its children is retrying
                    if (taskRun.getParentTaskRunId() != null) {
                        list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
                            .collect(Collectors.toCollection(ArrayList::new));
                    }
                }
            } else if (task instanceof LoopUntil waitFor && taskRun.getState().isRunning()) {
                if (waitFor.childTaskRunExecuted(executor.getExecution(), taskRun)) {
                    Map<String, Object> previousOutput = taskOutputService.getOutputs(taskRun);
                    RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution().withTaskRun(taskRun), taskRun);
                    Instant nextDate = waitFor.nextExecutionDate(runContext, executor.getExecution(), taskRun);
                    if (nextDate != null) {
                        Output newOutput = waitFor.outputs(previousOutput);
                        taskOutputService.saveOutputs(taskRun, newOutput);
                        executionDelays.add(
                            ExecutionDelay.builder()
                                .taskRunId(taskRun.getId())
                                .executionId(executor.getExecution().getId())
                                .date(nextDate)
                                .state(State.Type.RUNNING)
                                .delayType(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)
                                .build()
                        );
                        Execution execution = executionService.pauseFlowable(executor.getExecution(), taskRun);
                        executor.withExecution(execution, "pauseLoop");
                    } else {
                        executor.withExecution(executor.getExecution().withTaskRun(taskRun), "handleWaitFor");
                    }
                }
            } else if (task instanceof Pause pause && pause.getOnPause() != null) {
                // if a Pause task defines an onPause, we must create a TaskRun and a WorkerTask
                RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                WorkerTask pauseWorkerTask = WorkerTask.builder()
                    .data(WorkerTaskData.from(runContext))
                    .taskRun(
                        TaskRun.of(
                            executor.getExecution(),
                            ResolvedTask.of(pause.getOnPause())
                        )
                    )
                    .task(pause.getOnPause())
                    .executionKind(executor.getExecution().getKind())
                    .build();
                onPauses.add(new ExecutorContext.ExecutorWorkerTask(pauseWorkerTask, runContext));
            } else if (task instanceof Loop loop) {
                if (!loop.isMySubExecution(executor.getExecution(), taskRun)) {
                    if (taskRun.getState().isCreated()) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun);
                        try {
                            var valuesUri = FlowableUtils.resolveLoopValuesUri(runContext, loop.getValues());

                            if (valuesUri.isPresent()) {
                                var init = loop.initFromUri(runContext, valuesUri.get());
                                // save the iteration information in outputs to know how many loop iterations we already triggered
                                taskOutputService.saveOutputs(taskRun, Map.of(
                                    Loop.ITERATION_COUNT_OUTPUT, init.totalCount(),
                                    Loop.RUNNING_ITERATIONS_OUTPUT, init.limit(),
                                    Loop.TERMINATED_ITERATIONS_OUTPUT, 0,
                                    Loop.NEXT_OFFSET_OUTPUT, init.nextOffset())
                                );
                                for (int i = 0; i < init.values().size(); i++) {
                                    var loopExecution = executor.getExecution().loopExecution(taskRun, i, null, init.values().get(i));
                                    executor.withLoopExecution(loopExecution, "handleLoopExecution");
                                }
                            } else {
                                var init = loop.initFromValues(runContext);
                                // save the iteration information in outputs to know how many loop iterations we already triggered
                                taskOutputService.saveOutputs(taskRun, Map.of(
                                    Loop.ITERATION_COUNT_OUTPUT, init.totalCount(),
                                    Loop.RUNNING_ITERATIONS_OUTPUT, init.limit(),
                                    Loop.TERMINATED_ITERATIONS_OUTPUT, 0)
                                );

                                if (init.totalCount() == 0) {
                                    // if no loop iteration, we end the task immediately
                                    executor.withExecution(executor.getExecution()
                                        .withTaskRun(taskRun.withState(State.Type.SUCCESS)), "handleLoop");
                                    // replace existing CREATED WorkerTask to SUCCESS
                                    executor.getWorkerTasks().replaceAll(ewt ->
                                        ewt.workerTask().getTaskRun().getId().equals(taskRun.getId()) ?
                                        new ExecutorContext.ExecutorWorkerTask(ewt.workerTask().withTaskRun(taskRun.withState(State.Type.SUCCESS)), ewt.runContext()) :
                                        ewt
                                    );
                                }
                                else {
                                    if (init.values().isLeft()) {
                                        List<String> values = init.values().getLeft();
                                        for (int i = 0; i < init.limit(); i++) {
                                            var loopExecution = executor.getExecution().loopExecution(taskRun, i, null, values.get(i));
                                            executor.withLoopExecution(loopExecution, "handleLoopExecution");
                                        }
                                    } else {
                                        List<Pair<String, String>> values = init.values().getRight();
                                        for (int i = 0; i < init.limit(); i++) {
                                            var value = values.get(i);
                                            var loopExecution = executor.getExecution().loopExecution(taskRun, i, value.getKey(), value.getValue());
                                            executor.withLoopExecution(loopExecution, "handleLoopExecution");
                                        }
                                    }

                                    executor.withExecution(executor.getExecution()
                                        .withTaskRun(taskRun.withState(State.Type.RUNNING)), "handleLoop");
                                }
                            }
                        } catch (InternalException e) {
                            runContext.logger().error("Failed to handle loop execution: {}", e.getMessage(), e);
                            executor.withExecution(executor.getExecution()
                                .withTaskRun(taskRun.withState(State.Type.FAILED)), "handleLoop");
                            // replace existing CREATED WorkerTask to FAILED to prevent it from transitioning to RUNNING
                            executor.getWorkerTasks().replaceAll(ewt ->
                                ewt.workerTask().getTaskRun().getId().equals(taskRun.getId()) ?
                                new ExecutorContext.ExecutorWorkerTask(ewt.workerTask().withTaskRun(taskRun.withState(State.Type.FAILED)), ewt.runContext()) :
                                ewt
                            );
                        }
                    }
                }
            }

            // If the task is retrying
            // make sure that the workerTaskResult of the parent task is not sent
            if (taskRun.getState().isRetrying() && taskRun.getParentTaskRunId() != null) {
                list = list.stream().filter(workerTaskResult -> !workerTaskResult.getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            }

            // If the task is a flowable and is terminated, check that all children are terminated.
            // This may not be the case for parallel flowable tasks like Parallel, Dag, ForEach...
            // After a failed task, some child flowable may not be correctly terminated.
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
            .counter(
                MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT_DESCRIPTION,
                metricRegistry.tags(executor.getExecution())
            )
            .increment(executionDelays.size());

        executor.withWorkerTaskDelays(executionDelays, "handleChildWorkerTaskDelay");

        if (list.isEmpty()) {
            return executor;
        }

        if (!onPauses.isEmpty()) {
            List<TaskRun> taskRuns = onPauses.stream().map(executorTask -> executorTask.workerTask().getTaskRun()).toList();
            executor.withTaskRun(taskRuns, "handlePauses");
            executor.withWorkerTasks(onPauses, "handlePauses");
        }

        executor = this.handlePausedDelay(executor, list);

        this.addWorkerTaskResults(executor, list);

        return executor;
    }

    private AbstractRetry searchForParentRetry(TaskRun taskRun, ExecutorContext executor) {
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

    private ExecutorContext handlePausedDelay(ExecutorContext executor, List<WorkerTaskResult> workerTaskResults) throws InternalException {
        if (
            workerTaskResults
                .stream()
                .noneMatch(workerTaskResult -> workerTaskResult.getTaskRun().getState().getCurrent() == State.Type.PAUSED)
        ) {
            return executor;
        }

        List<ExecutionDelay> list = workerTaskResults
            .stream()
            .filter(workerTaskResult -> workerTaskResult.getTaskRun().getState().getCurrent() == State.Type.PAUSED)
            .map(throwFunction(workerTaskResult ->
            {
                Task task = executor.getFlow().findTaskByTaskId(workerTaskResult.getTaskRun().getTaskId());

                if (task instanceof Pause pauseTask) {
                    if (pauseTask.getPauseDuration() != null || pauseTask.getTimeout() != null) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        Duration duration = runContext.render(pauseTask.getPauseDuration()).as(Duration.class).orElse(null);
                        Duration timeout = runContext.render(pauseTask.getTimeout()).as(Duration.class).orElse(null);
                        Pause.Behavior behavior = runContext.render(pauseTask.getBehavior()).as(Pause.Behavior.class).orElse(Pause.Behavior.RESUME);
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

    private ExecutorContext handleCreatedKilling(ExecutorContext executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        List<WorkerTaskResult> workerTaskResults = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
            .map(
                t -> childWorkerTaskTypeToWorkerTask(
                    Optional.of(State.Type.KILLED),
                    t
                )
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        this.addWorkerTaskResults(executor, workerTaskResults);
        return executor;
    }

    private ExecutorContext handleAfterExecution(ExecutorContext executor) {
        if (!executor.getExecution().getState().isTerminated()) {
            return executor;
        }

        // execute afterExecution tasks
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

    private ExecutorContext handleEnd(ExecutorContext executor) throws InternalException {
        if (executor.getExecution().getState().isTerminated() || executor.getExecution().getState().isPaused() || executor.getExecution().getState().isRetrying()) {
            return executor;
        }

        List<ResolvedTask> currentTasks = null;
        if (executor.getExecution().getKind() != ExecutionKind.LOOP) {
            currentTasks = executor.getExecution().findTaskDependingFlowState(
                ResolvedTask.of(executor.getFlow().getTasks()),
                ResolvedTask.of(executor.getFlow().getErrors()),
                ResolvedTask.of(executor.getFlow().getFinally())
            );
        } else if (executor.getExecution().getLoopRun() != null) { // should always be true but better be safe
            Loop loop = (Loop) executor.getFlow().findTaskByTaskId(executor.getExecution().getLoopRun().taskId());
            currentTasks = executor.getExecution().findTaskDependingFlowState(
                ResolvedTask.of(loop.getTasks()),
                ResolvedTask.of(loop.getErrors()),
                ResolvedTask.of(loop.getFinally())
            );
        }

        if (currentTasks == null || !executor.getExecution().isTerminated(currentTasks)) {
            return executor;
        }

        return this.onEnd(executor);
    }

    private ExecutorContext handleRestart(ExecutorContext executor) {
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

    private ExecutorContext handleKilling(ExecutorContext executor) {
        if (executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        Execution newExecution = executor.getExecution().withState(State.Type.KILLED);

        return executor.withExecution(newExecution, "handleKilling");
    }

    private ExecutorContext handleWorkerTask(final ExecutorContext executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() == State.Type.KILLING) {
            return executor;
        }

        Optional<TextMapPropagator> textMapPropagator = openTelemetry
            .map(OpenTelemetry::getPropagators)
            .map(ContextPropagators::getTextMapPropagator);

        // submit TaskRun when receiving created, must be done after the state execution store
        Map<Boolean, List<ExecutorContext.ExecutorWorkerTask>> workerTasks = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated() && executor.getExecution().getFixtureForTaskRun(taskRun).isEmpty())
            .map(throwFunction(taskRun ->
            {
                Task task = executor.getFlow().findTaskByTaskId(taskRun.getTaskId());
                RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun);

                // inject the traceparent into the run context
                textMapPropagator.ifPresent(propagator -> propagator.inject(Context.current(), runContext, RunContextTextMapSetter.INSTANCE));

                WorkerTask workerTask = WorkerTask.builder()
                    .data(WorkerTaskData.from(runContext))
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
                    if (WorkerGroup.isDefault(workerGroupKey)) {
                        // Explicit default worker group - dispatch without existence check
                        return new ExecutorContext.ExecutorWorkerTask(workerTask, runContext);
                    }
                    if (workerGroupMetaStore.isWorkerGroupExistForKey(workerGroupKey, tenantId)) {
                        // Check whether at-least one worker is available
                        if (workerGroupMetaStore.isWorkerGroupAvailableForKey(workerGroupKey)) {
                            return new ExecutorContext.ExecutorWorkerTask(workerTask, runContext);
                        } else {
                            WorkerGroup.Fallback fallback = workerGroup.map(wg -> wg.getFallback()).orElse(WorkerGroup.Fallback.WAIT);
                            return switch (fallback) {
                                case FAIL -> {
                                    runContext.logger()
                                        .error("No workers are available for worker group '{}', failing the task.", workerGroupKey);
                                    yield new ExecutorContext.ExecutorWorkerTask(workerTask.withTaskRun(workerTask.getTaskRun().fail()), runContext);
                                }
                                case CANCEL -> {
                                    runContext.logger()
                                        .info("No workers are available for worker group '{}', canceling the task.", workerGroupKey);
                                    yield new ExecutorContext.ExecutorWorkerTask(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.CANCELLED)), runContext);
                                }
                                case WAIT -> {
                                    runContext.logger()
                                        .info("No workers are available for worker group '{}', waiting for one to be available.", workerGroupKey);
                                    yield new ExecutorContext.ExecutorWorkerTask(workerTask, runContext);
                                }
                            };
                        }
                    } else {
                        runContext.logger()
                            .error("Cannot run task. No worker group exist for key '{}'.", workerGroupKey);
                        // fail the task-run because no worker can run the task
                        return new ExecutorContext.ExecutorWorkerTask(workerTask.withTaskRun(workerTask.getTaskRun().fail()), runContext);
                    }
                } else {
                    return new ExecutorContext.ExecutorWorkerTask(workerTask, runContext);
                }
            })
            )
            .collect(
                Collectors.groupingBy(
                    executorTask -> executorTask.workerTask().getTaskRun().getState().isFailed() || executorTask.workerTask().getTaskRun().getState().getCurrent() == State.Type.CANCELLED
                )
            );

        // mock WorkerTaskResult for mocked execution
        // submit TaskRun when receiving created, must be done after the state execution store
        boolean hasMockedWorkerTask = false;
        record FixtureAndTaskRun(TaskFixture fixture, TaskRun taskRun) {
        }
        if (executor.getExecution().getFixtures() != null) {
            RunContext runContext = runContextInitializer.forExecutor(
                (DefaultRunContext) runContextFactory.of(
                    executor.getFlow(),
                    executor.getExecution()
                )
            );
            List<WorkerTaskResult> workerTaskResults = executor.getExecution()
                .getTaskRunList()
                .stream()
                .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
                .flatMap(taskRun -> executor.getExecution().getFixtureForTaskRun(taskRun).stream().map(fixture -> new FixtureAndTaskRun(fixture, taskRun)))
                .map(throwFunction(fixtureAndTaskRun ->
                {
                    AssetsDeclaration assetsDeclaration = executor.getFlow().findTaskByTaskId(fixtureAndTaskRun.taskRun.getTaskId()).getAssets();
                    return WorkerTaskResult.builder()
                        .taskRun(
                            fixtureAndTaskRun.taskRun()
                                .withState(Optional.ofNullable(fixtureAndTaskRun.fixture().getState()).orElse(State.Type.SUCCESS))
                                .withAssets(
                                    new AssetsInOut(
                                        Optional.ofNullable(assetsDeclaration).map(AssetsDeclaration::getInputs)
                                            .map(throwFunction(assetInputs -> runContext.render(assetInputs).asList(AssetIdentifier.class)))
                                            .stream()
                                            .flatMap(Collection::stream)
                                            .map(throwFunction(assetIdentifier -> assetIdentifier.withTenantId(executor.getFlow().getTenantId())))
                                            .toList(),
                                        fixtureAndTaskRun.fixture().getAssets() == null ? null
                                            : fixtureAndTaskRun.fixture().getAssets().stream()
                                                .map(asset -> asset.withTenantId(executor.getFlow().getTenantId()))
                                                .toList()
                                    )
                                )
                        )
                        .outputs(fixtureAndTaskRun.fixture().getOutputs() == null ? null : runContext.render(fixtureAndTaskRun.fixture().getOutputs()))
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

        ExecutorContext executorToReturn = executor;

        // suspend on breakpoint: if a breakpoint is for a CREATED taskrun, set the execution state to BREAKPOINT and ends here
        if (!ListUtils.isEmpty(executor.getExecution().getBreakpoints())) {
            List<Breakpoint> breakpoints = executor.getExecution().getBreakpoints();
            if (
                executor.getExecution()
                    .getTaskRunList()
                    .stream()
                    .anyMatch(taskRun -> shouldSuspend(taskRun, breakpoints))
            ) {
                List<TaskRun> newTaskRuns = executor.getExecution().getTaskRunList().stream().map(
                    taskRun ->
                    {
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
        List<ExecutorContext.ExecutorWorkerTask> endedTasks = workerTasks.get(true);
        if (endedTasks != null && !endedTasks.isEmpty()) {
            List<WorkerTaskResult> failed = endedTasks
                .stream()
                .map(executorTask -> WorkerTaskResult.builder().taskRun(executorTask.workerTask().getTaskRun()).build())
                .toList();

            this.addWorkerTaskResults(executor, failed);
        }

        // Send other TaskRun to the worker (create worker tasks)
        List<ExecutorContext.ExecutorWorkerTask> processingTasks = workerTasks.get(false);
        if (processingTasks != null && !processingTasks.isEmpty() && !executor.getExecution().getState().isBreakpoint()) {
            executorToReturn = executorToReturn.withWorkerTasks(processingTasks, "handleWorkerTask");

            metricRegistry
                .counter(MetricRegistry.METRIC_EXECUTOR_TASKRUN_CREATED_COUNT, MetricRegistry.METRIC_EXECUTOR_TASKRUN_CREATED_COUNT_DESCRIPTION, metricRegistry.tags(executor.getExecution()))
                .increment(processingTasks.size());
        }

        return executorToReturn;
    }

    private boolean shouldSuspend(TaskRun taskRun, List<Breakpoint> breakpoints) {
        return taskRun.getState().getCurrent().isCreated() && breakpoints.stream()
            .anyMatch(breakpoint -> taskRun.getTaskId().equals(breakpoint.getId()) && (breakpoint.getValue() == null || Objects.equals(taskRun.getValue(), breakpoint.getValue())));
    }

    private ExecutorContext handleExecutableTask(final ExecutorContext executor) {
        List<SubflowExecution<?>> executions = new ArrayList<>();
        List<SubflowExecutionResult> subflowExecutionResults = new ArrayList<>();

        boolean haveFlows = executor.getWorkerTasks()
            .removeIf(executorTask ->
            {
                WorkerTask workerTask = executorTask.workerTask();
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

                    // handle when
                    if (!TruthUtils.isTruthy(executorTask.runContext().render(workerTask.getTask().getWhen()))) {
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
                    List<SubflowExecution<?>> subflowExecutions = executableTask
                        .createSubflowExecutions(runContext, flowExecutorInterface, executor.getFlow(), executor.getExecution(), executableTaskRun);
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
                                    subflowExecution.getExecution(),
                                    subflowExecution.getOutputs()
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

        ExecutorContext resultExecutor = executor.withSubflowExecutions(executions, "handleExecutableTask");

        if (!subflowExecutionResults.isEmpty()) {
            resultExecutor = executor.withSubflowExecutionResults(subflowExecutionResults, "handleExecutableTaskWorkerTaskResults");
        }

        return resultExecutor;
    }

    private ExecutorContext handleExecutionUpdatingTask(final ExecutorContext executor) throws InternalException {
        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();

        executor.getWorkerTasks()
            .removeIf(executorTask ->
            {
                WorkerTask workerTask = executorTask.workerTask();
                if (!(workerTask.getTask() instanceof ExecutionUpdatableTask executionUpdatingTask)) {
                    return false;
                }

                try {
                    // Skip task if when condition is false
                    if (!TruthUtils.isTruthy(executorTask.runContext().render(workerTask.getTask().getWhen()))) {
                        executor.withExecution(
                            executor
                                .getExecution()
                                .withTaskRun(
                                    workerTask.getTaskRun().withState(State.Type.SKIPPED).addAttempt(TaskRunAttempt.builder().state(new State().withState(State.Type.SKIPPED)).build())
                                ),
                            "handleExecutionUpdatingTaskSkipped"
                        );
                        return false;
                    }

                    TaskRun runningTaskRun = workerTask
                        .getTaskRun()
                        .withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build()))
                        .withState(State.Type.RUNNING);

                    var newExecution = executionUpdatingTask.update(executor.getExecution(), executorTask.runContext());
                    if (newExecution.getState().getCurrent() == State.Type.KILLED) {
                        killQueue.emit(
                            ExecutionKilledExecution.builder()
                                .state(ExecutionKilled.State.REQUESTED)
                                .executionId(newExecution.getId())
                                .isOnKillCascade(true)
                                .tenantId(newExecution.getTenantId())
                                .build()
                        );
                    }
                    executor.withExecution(
                        newExecution.withTaskRun(runningTaskRun),
                        "handleExecutionUpdatingTask.updateExecution"
                    );

                    var terminalState = executionUpdatingTask
                        .resolveState(executorTask.runContext(), executor.getExecution())
                        .orElse(State.Type.SUCCESS);

                    TaskRunAttempt terminalAttempt = runningTaskRun.lastAttempt().withState(terminalState);

                    workerTaskResults.add(
                        WorkerTaskResult.builder()
                            .taskRun(
                                runningTaskRun
                                    .withAttempts(List.of(terminalAttempt))
                                    .withState(terminalState)
                            )
                            .build()
                    );
                } catch (Exception e) {
                    workerTaskResults.add(
                        WorkerTaskResult.builder()
                            .taskRun(workerTask.getTaskRun().fail())
                            .build()
                    );
                    executor.withException(e, "handleExecutionUpdatingTask");
                }
                return true;
            });

        this.addWorkerTaskResults(executor, workerTaskResults);

        return executor;
    }

    public void addWorkerTaskResults(ExecutorContext executor, List<WorkerTaskResult> workerTaskResults) throws InternalException {
        for (WorkerTaskResult workerTaskResult : workerTaskResults) {
            this.addWorkerTaskResult(executor, () -> executor.getFlow(), workerTaskResult);
        }
    }

    public void addWorkerTaskResult(ExecutorContext executor, Supplier<FlowWithSource> flow, WorkerTaskResult workerTaskResult) throws InternalException {
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

            // outputs
            taskOutputService.saveOutputs(taskRun, workerTaskResult.getOutputs());

            ExecutionKind executionKind = Optional.ofNullable(executor.getExecution().getKind()).orElse(ExecutionKind.NORMAL);
            if (
                taskRun.getAssets() != null &&
                    (!taskRun.getAssets().getInputs().isEmpty() || !taskRun.getAssets().getOutputs().isEmpty())
                    && executionKind != ExecutionKind.TEST
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
                    taskRun.getAssets().getOutputs().forEach(asset ->
                    {
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
        List<TaskRun> taskRuns = new ArrayList<>(ListUtils.emptyOnNull(execution.getTaskRunList()));

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

    public void log(Logger log, boolean in, WorkerJob value) {
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
                    workerTrigger.uid()
                );
            }
        }
    }

    public void log(Logger log, boolean in, WorkerTaskResult value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getTaskRun().toStringState()
            );
        }
    }

    public void log(Logger log, boolean in, SubflowExecutionResult value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getParentTaskRun().toStringState()
            );
        }
    }

    public void log(Logger log, boolean in, SubflowExecutionEnd value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.toStringState()
            );
        }
    }

    public void log(Logger log, boolean in, Execution value) {
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

    public void log(Logger log, boolean in, ExecutorContext value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} [key='{}', from='{}', crc32='{}']\n{}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.getExecution().getId(),
                value.getFrom(),
                value.getExecution().toCrc32State(),
                value.getExecution().toStringState()
            );
        }
    }

    public void log(Logger log, boolean in, TerminatedLoopExecution value) {
        if (log.isDebugEnabled()) { // taskRun().toStringState() is costly so we avoid calling it if not needed
            log.debug(
                "{} {} : {}",
                in ? "<< IN " : ">> OUT",
                value.getClass().getSimpleName(),
                value.toStringState()
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
     *
     * @see #processViolation(RunContext, ExecutorContext, Violation)
     *      <p>
     *      WARNING: ATM, only the first violation will update the execution.
     */
    public ExecutorContext handleExecutionChangedSLA(ExecutorContext executor) throws QueueException {
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
    public ExecutorContext processViolation(RunContext runContext, ExecutorContext executor, Violation violation) throws QueueException {
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
            .map(taskRun ->
            {
                try {
                    return execution.withTaskRun(taskRun.withState(state));
                } catch (InternalException e) {
                    // in case we cannot update the last not terminated task run, we ignore it
                    return execution;
                }
            })
            .orElse(execution)
            .withState(state);

        killQueue.emit(
            ExecutionKilledExecution
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
