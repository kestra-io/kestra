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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractExecutor implements Runnable, Closeable {
    protected RunContextFactory runContextFactory;
    protected MetricRegistry metricRegistry;
    protected ConditionService conditionService;

    public AbstractExecutor(
        RunContextFactory runContextFactory,
        MetricRegistry metricRegistry,
        ConditionService conditionService
    ) {
        this.runContextFactory = runContextFactory;
        this.metricRegistry = metricRegistry;
        this.conditionService = conditionService;
    }

    public Executor process(Executor executor) {
        try {
            executor = this.handleEnd(executor);
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
            executor = this.handleChildWorkerCreatedKilling(executor);
            executor = this.handleChildWorkerTaskResult(executor);
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
                .counter(MetricRegistry.KESTRA_EXECUTOR_EXECUTION_STARTED_COUNT, metricRegistry.tags(execution))
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
            .counter(MetricRegistry.KESTRA_EXECUTOR_TASKRUN_NEXT_COUNT, metricRegistry.tags(execution))
            .increment(nexts.size());

        return newExecution;
    }

    private Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun parentTaskRun) throws InternalException {
        Task parent = flow.findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent;

            RunContext runContext = runContextFactory.of(flow, parent, execution, parentTaskRun);

            // first find the normal ended child tasks and send result
            Optional<WorkerTaskResult> endedTask = childWorkerTaskTypeToWorkerTask(
                flowableParent
                    .resolveState(runContext, execution, parentTaskRun),
                parent,
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
                        parent,
                        parentTaskRun
                    );
                }

                // Then wait for completion (KILLED or whatever) on child taks to KILLED the parennt one.
                List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
                    flowableParent.childTasks(runContext, parentTaskRun),
                    FlowableUtils.resolveTasks(flowableParent.getErrors(), parentTaskRun)
                );

                List<TaskRun> taskRunByTasks = execution.findTaskRunByTasks(currentTasks, parentTaskRun);

                if (taskRunByTasks.stream().filter(t -> t.getState().isTerninated()).count() == taskRunByTasks.size()) {
                    return childWorkerTaskTypeToWorkerTask(
                        Optional.of(State.Type.KILLED),
                        parent,
                        parentTaskRun
                    );
                }
            }
        }

        return Optional.empty();
    }

    private Optional<WorkerTaskResult> childWorkerTaskTypeToWorkerTask(
        Optional<State.Type> findState,
        Task task,
        TaskRun taskRun
    ) {
        return findState
            .map(throwFunction(type -> new WorkerTaskResult(
                taskRun.withState(type),
                task
            )))
            .stream()
            .peek(workerTaskResult -> {
                metricRegistry
                    .counter(
                        MetricRegistry.KESTRA_EXECUTOR_WORKERTASKRESULT_COUNT,
                        metricRegistry.tags(workerTaskResult)
                    )
                    .increment();

            })
            .findFirst();
    }

    private List<TaskRun> childNextsTaskRun(Executor executor, TaskRun parentTaskRun) throws InternalException {
        Task parent = executor.getFlow().findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent;

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

        return new ArrayList<>();
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
                    log.warn("Unable to save output on taskRun '{}'", taskRun, e);
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
            .counter(MetricRegistry.KESTRA_EXECUTOR_EXECUTION_END_COUNT, metricRegistry.tags(newExecution))
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_EXECUTION_DURATION, metricRegistry.tags(newExecution))
            .record(newExecution.getState().getDuration());

        return executor.withExecution(newExecution, "onEnd");
    }

    private Executor handleNext(Executor Executor) {
        List<TaskRun> nexts = this.saveFlowableOutput(
            FlowableUtils
                .resolveSequentialNexts(
                    Executor.getExecution(),
                    ResolvedTask.of(Executor.getFlow().getTasks()),
                    ResolvedTask.of(Executor.getFlow().getErrors())
                ),
            Executor,
            null
        );

        if (nexts.size() == 0) {
            return Executor;
        }

        return Executor.withTaskRun(nexts, "handleNext");
    }

    private Executor handleChildNext(Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null) {
            return executor;
        }

        List<TaskRun> running = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .collect(Collectors.toList());

        // Remove functionnal style to avoid (class io.kestra.core.exceptions.IllegalVariableEvaluationException cannot be cast to class java.lang.RuntimeException'
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

        return executor.withWorkerTaskResults(list, "handleChildWorkerTaskResult");
    }

    private Executor handleChildWorkerCreatedKilling(Executor executor) throws InternalException {
        if (executor.getExecution().getTaskRunList() == null || executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        List<WorkerTaskResult> workerTaskResults = executor.getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .map(throwFunction(t -> {
                Task task = executor.getFlow().findTaskByTaskId(t.getTaskId());

                return childWorkerTaskTypeToWorkerTask(
                    Optional.of(State.Type.KILLED),
                    task,
                    t
                );
            }))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        return executor.withWorkerTaskResults(workerTaskResults, "handleChildWorkerCreatedKilling");
    }


    private Executor handleListeners(Executor executor) {
        if (!executor.getExecution().getState().isTerninated()) {
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
        if (executor.getExecution().getState().isTerninated()) {
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

    private Executor handleKilling(Executor executor) {
        if (executor.getExecution().getState().getCurrent() != State.Type.KILLING) {
            return executor;
        }

        List<ResolvedTask> currentTasks = executor.getExecution().findTaskDependingFlowState(
            ResolvedTask.of(executor.getFlow().getTasks()),
            ResolvedTask.of(executor.getFlow().getErrors())
        );

        if (executor.getExecution().hasRunning(currentTasks) || executor.getExecution().findFirstByState(State.Type.CREATED).isPresent()) {
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
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
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

    @Getter
    @AllArgsConstructor
    public static class Executor {
        private Execution execution;
        private Exception exception;
        private final List<String> from = new ArrayList<>();
        private final Long offset;
        private boolean executionUpdated = false;
        private Flow flow;
        private final List<TaskRun> nexts = new ArrayList<>();
        private final List<WorkerTask> workerTasks = new ArrayList<>();
        private final List<WorkerTaskResult> workerTaskResults = new ArrayList<>();

        public Executor(Execution execution, Long offset) {
            this.execution = execution;
            this.offset = offset;
        }

        public Executor withFlow(Flow flow) {
            this.flow = flow;

            return this;
        }

        public Executor withExecution(Execution execution, String from) {
            this.execution = execution;
            this.from.add(from);
            this.executionUpdated = true;

            return this;
        }

        public Executor withException(Exception exception, String from) {
            this.exception = exception;
            this.from.add(from);
            this.executionUpdated = true;

            return this;
        }

        public Executor withTaskRun(List<TaskRun> taskRuns, String from) {
            this.nexts.addAll(taskRuns);
            this.from.add(from);

            return this;
        }

        public Executor withWorkerTasks(List<WorkerTask> workerTasks, String from) {
            this.workerTasks.addAll(workerTasks);
            this.from.add(from);

            return this;
        }

        public Executor withWorkerTaskResults(List<WorkerTaskResult> workerTaskResults, String from) {
            this.workerTaskResults.addAll(workerTaskResults);
            this.from.add(from);

            return this;
        }
    }
}
