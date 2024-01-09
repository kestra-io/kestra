package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.*;
import io.kestra.core.services.ConditionService;
import io.kestra.core.tasks.flows.Pause;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class ExecutorService {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected RunContextFactory runContextFactory;

    @Inject
    protected MetricRegistry metricRegistry;

    @Inject
    protected ConditionService conditionService;

    protected FlowExecutorInterface flowExecutorInterface;

    protected FlowExecutorInterface flowExecutorInterface() {
        // bean is injected late, so we need to wait
        if (this.flowExecutorInterface == null) {
            this.flowExecutorInterface = applicationContext.getBean(FlowExecutorInterface.class);
        }

        return this.flowExecutorInterface;
    }

    public Executor checkConcurrencyLimit(Executor executor, Flow flow, Execution execution, long count) {
        if (count >= flow.getConcurrency().getLimit()) {
            return switch (flow.getConcurrency().getBehavior()) {
                case QUEUE -> {
                    var newExecution = execution.withState(State.Type.QUEUED);

                    ExecutionQueued executionQueued = ExecutionQueued.builder()
                        .tenantId(flow.getTenantId())
                        .namespace(flow.getNamespace())
                        .flowId(flow.getId())
                        .date(Instant.now())
                        .execution(newExecution)
                        .build();

                    // when max concurrency is reached, we throttle the execution and stop processing
                    flow.logger().info(
                        "[namespace: {}] [flow: {}] [execution: {}] Flow is queued due to concurrency limit exceeded, {} running(s)",
                        newExecution.getNamespace(),
                        newExecution.getFlowId(),
                        newExecution.getId(),
                        count
                    );
                    // return the execution queued
                    yield executor
                        .withExecutionQueued(executionQueued)
                        .withExecution(newExecution, "checkConcurrencyLimit");
                }
                case CANCEL -> executor.withExecution(execution.withState(State.Type.CANCELLED), "checkConcurrencyLimit");
                case FAIL -> executor.withException(new IllegalStateException("Flow is FAILED due to concurrency limit exceeded"), "checkConcurrencyLimit");
            };
        }

        return executor;
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
            log.trace(
                "[namespace: {}] [flow: {}] [execution: {}] Found {} next(s) {}",
                execution.getNamespace(),
                execution.getFlowId(),
                execution.getId(),
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

            flow.logger().info(
                "[namespace: {}] [flow: {}] [execution: {}] Flow started",
                execution.getNamespace(),
                execution.getFlowId(),
                execution.getId()
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
                    return this.saveFlowableOutput(
                        nexts,
                        executor,
                        parentTaskRun
                    );
                }
            } catch (Exception e) {
                log.warn("Unable to resolve the next tasks to run", e);
            }
        }

        return Collections.emptyList();
    }

    private List<TaskRun> saveFlowableOutput(
        List<NextTaskRun> nextTaskRuns,
        Executor executor,
        TaskRun parentTaskRun
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

                    Output outputs = flowableTask.outputs(runContext, executor.getExecution(), parentTaskRun);
                    taskRun = taskRun.withOutputs(outputs != null ? outputs.toMap() : ImmutableMap.of());
                } catch (Exception e) {
                    executor.getFlow().logger().warn("Unable to save output on taskRun '{}'", taskRun, e);
                }

                return taskRun;
            }))
            .collect(Collectors.toList());
    }

    private Executor onEnd(Executor executor) {
        Execution newExecution = executor.getExecution()
            .withState(executor.getExecution().guessFinalState(executor.getFlow()));

        Logger logger = executor.getFlow().logger();

        logger.info(
            "[namespace: {}] [flow: {}] [execution: {}] Flow completed with state {} in {}",
            newExecution.getNamespace(),
            newExecution.getFlowId(),
            newExecution.getId(),
            newExecution.getState().getCurrent(),
            newExecution.getState().humanDuration()
        );

        if (logger.isTraceEnabled()) {
            logger.debug(newExecution.toString(true));
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
            this.saveFlowableOutput(nextTaskRuns, executor, null),
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

    private Executor handleChildWorkerTaskResult(Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<WorkerTaskResult> list = new ArrayList<>();

        for (TaskRun taskRun : executor.getExecution().getTaskRunList()) {
            if (taskRun.getState().isRunning()) {
                Optional<WorkerTaskResult> workerTaskResult = this.childWorkerTaskResult(
                    executor.getFlow(),
                    executor.getExecution(),
                    taskRun
                );

                workerTaskResult.ifPresent(list::add);
            }
        }

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
                            .build();
                    }
                }

                return null;
            }))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

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
            .collect(Collectors.toList());

        return executor.withWorkerTaskResults(workerTaskResults, "handleChildWorkerCreatedKilling");
    }


    private Executor handleListeners(Executor executor) {
        if (!executor.getExecution().getState().isTerminated()) {
            return executor;
        }

        List<ResolvedTask> currentTasks = conditionService.findValidListeners(executor.getFlow(), executor.getExecution());

        List<TaskRun> nexts = this.saveFlowableOutput(
            FlowableUtils.resolveSequentialNexts(
                executor.getExecution(),
                currentTasks
            ),
            executor,
            null
        );

        if (nexts.isEmpty()) {
            return executor;
        }

        return executor.withTaskRun(nexts, "handleListeners");
    }

    private Executor handleEnd(Executor executor) {
        if (executor.getExecution().getState().isTerminated() || executor.getExecution().getState().isPaused()) {
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

        executor.getFlow().logger().info(
            "[namespace: {}] [flow: {}] [execution: {}] Flow restarted",
            executor.getExecution().getNamespace(),
            executor.getExecution().getFlowId(),
            executor.getExecution().getId()
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


    private Executor handleWorkerTask(Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() == State.Type.KILLING) {
            return executor;
        }

        // submit TaskRun when receiving created, must be done after the state execution store
        List<WorkerTask> workerTasks = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent().isCreated())
            .map(throwFunction(taskRun -> {
                Task task = executor.getFlow().findTaskByTaskId(taskRun.getTaskId());
                RunContext runContext = runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun);
                return WorkerTask.builder()
                    .runContext(runContext)
                    .taskRun(taskRun)
                    .task(task)
                    .build();
            }))
            .collect(Collectors.toList());

        if (workerTasks.isEmpty()) {
            return executor;
        }

        return executor.withWorkerTasks(workerTasks, "handleWorkerTask");
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
                    }
                    else {
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
                                subflowExecutionResult.ifPresent(result -> subflowExecutionResults.add(result));
                            }
                        }
                    }
                }
                catch (Exception e) {
                    WorkerTaskResult failed = WorkerTaskResult.builder()
                        .taskRun(workerTask.getTaskRun().withState(State.Type.FAILED)
                            .withAttempts(Collections.singletonList(
                                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build()
                            ))
                        )
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
                        .taskRun(workerTask.getTaskRun().withState(State.Type.FAILED)
                            .withAttempts(Collections.singletonList(
                                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build()
                            ))
                        )
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
        if (workerTaskResult.getDynamicTaskRuns() != null) {
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
        }
        else if (value instanceof WorkerTrigger workerTrigger){
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

    public void log(Logger log, Boolean in, ExecutionKilled value) {
        log.debug(
            "{} {} [key='{}']\n{}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getExecutionId(),
            value
        );
    }
}
