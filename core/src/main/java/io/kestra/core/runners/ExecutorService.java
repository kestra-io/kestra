package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.services.ConditionService;
import io.kestra.core.tasks.flows.Worker;
import io.kestra.core.tasks.flows.Pause;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

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

    public Executor process(Executor executor) {
        // previous failed (flow join can fail), just forward
        if (!executor.canBeProcessed()) {
            return executor;
        }

        try {
            executor = this.handleRestart(executor);
            executor = this.handleEnd(executor);
            // if killing: move created tasks to killed as they are not already started
            executor = this.handleCreatedKilling(executor);
            // if all tasks are  killed or terminated, set the execution to killed
            executor = this.handleKilling(executor);

            // killing, so no more nexts
            if (executor.getExecution().getState().getCurrent() != State.Type.KILLING && executor.getExecution().getState().getCurrent() != State.Type.KILLED) {
                executor = this.handleNext(executor);
                executor = this.handleChildNext(executor);
            }

            // but keep listeners on killing
            executor = this.handleListeners(executor);

            // search for worker task
            executor = this.handleWorkerTask(executor);

            // search for worker task result
            executor = this.handleChildWorkerTaskResult(executor);

            // search for flow task
            executor = this.handleFlowTask(executor);
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
            Optional<WorkerTaskResult> endedTask = childWorkerTaskTypeToWorkerTask(
                flowableParent
                    .resolveState(runContext, execution, parentTaskRun),
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

            if (nexts.size() > 0) {
                return this.saveFlowableOutput(
                    nexts,
                    executor,
                    parentTaskRun
                );
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

                    taskRun = taskRun.withOutputs(
                        flowableTask.outputs(runContext, executor.getExecution(), parentTaskRun) != null ?
                            flowableTask.outputs(runContext, executor.getExecution(), parentTaskRun).toMap() :
                            ImmutableMap.of()
                    );
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

    private Executor handleNext(Executor Executor) {
        List<NextTaskRun> nextTaskRuns = FlowableUtils
            .resolveSequentialNexts(
                Executor.getExecution(),
                ResolvedTask.of(Executor.getFlow().getTasks()),
                ResolvedTask.of(Executor.getFlow().getErrors())
            );

        if (nextTaskRuns.size() == 0) {
            return Executor;
        }

        return Executor.withTaskRun(
            this.saveFlowableOutput(nextTaskRuns, Executor, null),
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

        if (result.size() == 0) {
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

        if (list.size() == 0) {
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

                if (task instanceof Pause) {
                    Pause pauseTask = (Pause) task;

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

        if(executor.getExecution().getState().getCurrent() != State.Type.PAUSED) {
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
            .map(throwFunction(t -> {
                Task task = executor.getFlow().findTaskByTaskId(t.getTaskId());

                return childWorkerTaskTypeToWorkerTask(
                    Optional.of(State.Type.KILLED),
                    t
                );
            }))
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

        if (nexts.size() == 0) {
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

        List<ResolvedTask> currentTasks = executor.getExecution().findTaskDependingFlowState(
            ResolvedTask.of(executor.getFlow().getTasks()),
            ResolvedTask.of(executor.getFlow().getErrors())
        );

        if (executor.getExecution().hasRunning(currentTasks) || executor.getExecution().hasCreated()) {
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

                return WorkerTask.builder()
                    .runContext(runContextFactory.of(executor.getFlow(), task, executor.getExecution(), taskRun))
                    .taskRun(taskRun)
                    .task(task)
                    .build();
            }))
            .collect(Collectors.toList());

        if (workerTasks.size() == 0) {
            return executor;
        }

        return executor.withWorkerTasks(workerTasks, "handleWorkerTask");
    }

    private Executor handleFlowTask(final Executor executor) {
        List<WorkerTaskExecution> executions = new ArrayList<>();
        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();

        boolean haveFlows = executor.getWorkerTasks()
            .removeIf(workerTask -> {
                if (!(workerTask.getTask() instanceof io.kestra.core.tasks.flows.Flow)) {
                    return false;
                }

                io.kestra.core.tasks.flows.Flow flowTask = (io.kestra.core.tasks.flows.Flow) workerTask.getTask();
                RunContext runContext = runContextFactory.of(
                    executor.getFlow(),
                    flowTask,
                    executor.getExecution(),
                    workerTask.getTaskRun()
                );

                try {
                    Execution execution = flowTask.createExecution(runContext, flowExecutorInterface());

                    WorkerTaskExecution workerTaskExecution = WorkerTaskExecution.builder()
                        .task(flowTask)
                        .taskRun(workerTask.getTaskRun())
                        .execution(execution)
                        .build();

                    executions.add(workerTaskExecution);

                    if (!flowTask.getWait()) {
                        workerTaskResults.add(flowTask.createWorkerTaskResult(
                            null,
                            workerTaskExecution,
                            null,
                            execution
                        ));
                    }
                } catch (Exception e) {
                    workerTaskResults.add(WorkerTaskResult.builder()
                        .taskRun(workerTask.getTaskRun().withState(State.Type.FAILED))
                        .build()
                    );
                    executor.withException(e, "handleFlowTask");
                }

                return true;
            });

        if (!haveFlows) {
            return executor;
        }

        Executor resultExecutor = executor.withWorkerTaskExecutions(executions, "handleFlowTask");

        if (workerTaskResults.size() > 0) {
            resultExecutor = executor.withWorkerTaskResults(workerTaskResults, "handleFlowTaskWorkerTaskResults");
        }

        return resultExecutor;
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

                if (parentTask instanceof Worker) {
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

    public void log(Logger log, Boolean in, WorkerTask value) {
        log.debug(
            "{} {} : {}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getTaskRun().toStringState()
        );
    }

    public void log(Logger log, Boolean in, WorkerTaskResult value) {
        log.debug(
            "{} {} : {}",
            in ? "<< IN " : ">> OUT",
            value.getClass().getSimpleName(),
            value.getTaskRun().toStringState()
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
}
