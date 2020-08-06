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

    public AbstractExecutor(RunContextFactory runContextFactory, MetricRegistry metricRegistry) {
        this.runContextFactory = runContextFactory;
        this.metricRegistry = metricRegistry;
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

    private Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException, InternalException {
        RunContext runContext = runContextFactory.of(flow, execution);
        ResolvedTask parent = flow.findTaskByTaskRun(taskRun, runContext);

        if (parent.getTask() instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent.getTask();

            return flowableParent
                .resolveState(runContext, execution, taskRun)
                .map(throwFunction(type -> new WorkerTaskResult(
                    taskRun
                        .withState(type)
                        .withOutputs(
                            flowableParent.outputs(runContext, execution, taskRun) != null ?
                                flowableParent.outputs(runContext, execution, taskRun).toMap() :
                                ImmutableMap.of()
                        ),
                    parent.getTask()
                )))
                .stream()
                .peek(workerTaskResult -> {
                    metricRegistry
                        .counter(MetricRegistry.KESTRA_EXECUTOR_WORKERTASKRESULT_COUNT, metricRegistry.tags(workerTaskResult))
                        .increment();

                })
                .findFirst();
        }

        return Optional.empty();
    }

    private Optional<List<TaskRun>> childNextsTaskRun(Flow flow, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException, InternalException {
        ResolvedTask parent = flow.findTaskByTaskRun(taskRun, runContextFactory.of(flow, execution));

        if (parent.getTask() instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent.getTask();
            List<TaskRun> nexts = flowableParent.resolveNexts(runContextFactory.of(flow, execution), execution, taskRun);

            if (nexts.size() > 0) {
                return Optional.of(nexts);
            }
        }

        return Optional.empty();
    }

    private Execution onEnd(Flow flow, Execution execution) {
        Execution newExecution = execution.withState(
            execution.hasFailed() ? State.Type.FAILED : State.Type.SUCCESS
        );

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
        return this.handleEnd(execution, flow);
    }

    protected Optional<List<TaskRun>> doNexts(Execution execution, Flow flow) throws Exception {
        List<TaskRun> nexts;

        nexts = this.handleNext(execution, flow);
        if (nexts.size() > 0) {
            return Optional.of(nexts);
        }

        nexts = this.handleChildNext(execution, flow);
        if (nexts.size() > 0) {
            return Optional.of(nexts);
        }

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
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING)
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
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING)
            .map(throwFunction(taskRun -> {
                return this.childWorkerTaskResult(flow, execution, taskRun);
            }))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private List<TaskRun> handleListeners(Execution execution, Flow flow) {
        if (!execution.getState().isTerninated()) {
            return new ArrayList<>();
        }

        List<ResolvedTask> currentTasks = execution.findValidListeners(flow);

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

    private List<WorkerTask> handleWorkerTask(Execution execution, Flow flow) throws Exception {
        if (execution.getTaskRunList() == null) {
            return new ArrayList<>();
        }

        // submit TaskRun when receiving created, must be done after the state execution store
        return execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .map(throwFunction(taskRun -> {
                ResolvedTask resolvedTask = flow.findTaskByTaskRun(
                    taskRun,
                    runContextFactory.of(flow, execution)
                );

                return  WorkerTask.builder()
                    .runContext(runContextFactory.of(flow, resolvedTask, execution, taskRun))
                    .taskRun(taskRun)
                    .task(resolvedTask.getTask())
                    .build();
                }))
            .collect(Collectors.toList());
    }
}
