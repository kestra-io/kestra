package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.services.ConditionService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractExecutor implements Runnable {
    protected RunContextFactory runContextFactory;
    protected MetricRegistry metricRegistry;
    protected ConditionService conditionService;

    public AbstractExecutor(RunContextFactory runContextFactory, MetricRegistry metricRegistry, ConditionService conditionService) {
        this.runContextFactory = runContextFactory;
        this.metricRegistry = metricRegistry;
        this.conditionService = conditionService;
    }

    protected Execution onNexts(Flow flow, Execution execution, List<TaskRun> nexts) {
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

    private Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException, InternalException {
        Task parent = flow.findTaskByTaskId(parentTaskRun.getTaskId());

        if (parent instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent;

            RunContext runContext = runContextFactory.of(flow, parent, execution, parentTaskRun);

            // first find the normal ended child tasks and send result
            Optional<WorkerTaskResult> endedTask = childWorkerTaskTypeToWorkerTask(
                flowableParent
                    .resolveState(runContext, execution, parentTaskRun),
                parent,
                parentTaskRun,
                runContext,
                execution
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
                        parentTaskRun,
                        runContext,
                        execution
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
                        parentTaskRun,
                        runContext,
                        execution
                    );
                }
            }
        }

        return Optional.empty();
    }

    private Optional<WorkerTaskResult> childWorkerTaskTypeToWorkerTask(
        Optional<State.Type> findState,
        Task parentTask,
        TaskRun parentTaskRun,
        RunContext runContext,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        FlowableTask<?> flowableParent = (FlowableTask<?>) parentTask;

        return findState
            .map(throwFunction(type -> new WorkerTaskResult(
                parentTaskRun
                    .withState(type)
                    .withOutputs(
                        flowableParent.outputs(runContext, execution, parentTaskRun) != null ?
                            flowableParent.outputs(runContext, execution, parentTaskRun).toMap() :
                            ImmutableMap.of()
                    ),
                parentTask
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

    private Optional<List<TaskRun>> childNextsTaskRun(Flow flow, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException, InternalException {
        Task parent = flow.findTaskByTaskId(taskRun.getTaskId());

        if (parent instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent;
            List<TaskRun> nexts = flowableParent.resolveNexts(
                runContextFactory.of(
                    flow,
                    parent,
                    execution,
                    taskRun
                ),
                execution,
                taskRun
            );

            if (nexts.size() > 0) {
                return Optional.of(nexts);
            }
        }

        return Optional.empty();
    }

    private Execution onEnd(Flow flow, Execution execution) {
        Execution newExecution = execution.withState(execution.guessFinalState());

        Logger logger = flow.logger();

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

        return newExecution;
    }

    protected Optional<Execution> doMain(Execution execution, Flow flow) {
        Optional<Execution> end = this.handleEnd(execution, flow);
        if (end.isPresent()) {
            return end;
        }

        return this.handleKilling(execution, flow);
    }

    protected Optional<List<TaskRun>> doNexts(Execution execution, Flow flow) throws Exception {
        List<TaskRun> nexts;

        // killing, so no more nexts
        if (execution.getState().getCurrent() != State.Type.KILLING && execution.getState().getCurrent() != State.Type.KILLED) {
            nexts = this.handleNext(execution, flow);
            if (nexts.size() > 0) {
                return Optional.of(nexts);
            }

            nexts = this.handleChildNext(execution, flow);
            if (nexts.size() > 0) {
                return Optional.of(nexts);
            }
        }

        // but keep listeners on killing
        nexts = this.handleListeners(execution, flow);
        if (nexts.size() > 0) {
            return Optional.of(nexts);
        }

        return Optional.empty();
    }

    protected Optional<List<WorkerTask>> doWorkerTask(Execution execution, Flow flow) throws Exception {
        List<WorkerTask> nexts;

        nexts = this.handleWorkerTask(execution, flow);
        if (nexts.size() > 0) {
            return Optional.of(nexts);
        }

        return Optional.empty();
    }

    protected Optional<List<WorkerTaskResult>> doWorkerTaskResult(Execution execution, Flow flow) throws Exception {
        List<WorkerTaskResult> nexts;

        nexts = this.handleChildWorkerTaskResult(execution, flow);
        if (nexts.size() > 0) {
            return Optional.of(nexts);
        }

        return Optional.empty();
    }

    private List<TaskRun> handleNext(Execution execution, Flow flow) {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            ResolvedTask.of(flow.getTasks()),
            ResolvedTask.of(flow.getErrors())
        );
    }

    private List<TaskRun> handleChildNext(Execution execution, Flow flow) throws Exception {
        if (execution.getTaskRunList() == null) {
            return new ArrayList<>();
        }

        return execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .flatMap(throwFunction(taskRun -> this.childNextsTaskRun(flow, execution, taskRun)
                .orElse(new ArrayList<>())
                .stream()
            ))
            .collect(Collectors.toList());
    }

    private List<WorkerTaskResult> handleChildWorkerTaskResult(Execution execution, Flow flow) throws Exception {
        if (execution.getTaskRunList() == null) {
            return new ArrayList<>();
        }

        return execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .map(throwFunction(taskRun -> this.childWorkerTaskResult(flow, execution, taskRun)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private List<TaskRun> handleListeners(Execution execution, Flow flow) {
        if (!execution.getState().isTerninated()) {
            return new ArrayList<>();
        }

        List<ResolvedTask> currentTasks = conditionService.findValidListeners(flow, execution);

        return FlowableUtils.resolveSequentialNexts(
            execution,
            currentTasks,
            new ArrayList<>()
        );
    }

    private Optional<Execution> handleEnd(Execution execution, Flow flow) {
        if (execution.getState().isTerninated()) {
            return Optional.empty();
        }

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            ResolvedTask.of(flow.getTasks()),
            ResolvedTask.of(flow.getErrors())
        );

        if (!execution.isTerminated(currentTasks)) {
            return Optional.empty();
        }

        return Optional.of(this.onEnd(flow, execution));
    }

    private Optional<Execution> handleKilling(Execution execution, Flow flow) {
        if (execution.getState().getCurrent() != State.Type.KILLING) {
            return Optional.empty();
        }

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            ResolvedTask.of(flow.getTasks()),
            ResolvedTask.of(flow.getErrors())
        );

        if (execution.hasRunning(currentTasks) || execution.findFirstByState(State.Type.CREATED).isPresent()) {
            return Optional.empty();
        }

        Execution newExecution = execution.withState(State.Type.KILLED);

        return Optional.of(newExecution);
    }


    private List<WorkerTask> handleWorkerTask(Execution execution, Flow flow) throws Exception {
        if (execution.getTaskRunList() == null || execution.getState().getCurrent() == State.Type.KILLING) {
            return new ArrayList<>();
        }

        // submit TaskRun when receiving created, must be done after the state execution store
        return execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .map(throwFunction(taskRun -> {
                Task task = flow.findTaskByTaskId(taskRun.getTaskId());

                return WorkerTask.builder()
                    .runContext(runContextFactory.of(flow, task, execution, taskRun))
                    .taskRun(taskRun)
                    .task(task)
                    .build();
                }))
            .collect(Collectors.toList());
    }
}
