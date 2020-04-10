package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
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

import static org.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractExecutor implements Runnable {
    protected ApplicationContext applicationContext;
    protected MetricRegistry metricRegistry;

    public AbstractExecutor(ApplicationContext applicationContext, MetricRegistry metricRegistry) {
        this.applicationContext = applicationContext;
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
                .timer(MetricRegistry.KESTRA_EXECUTOR_EXECUTION_STARTED_COUNT, metricRegistry.tags(execution))
                .record(newExecution.getState().getDuration());

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

    protected Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException, InternalException {
        RunContext runContext = new RunContext(this.applicationContext, flow, execution);
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

    protected Optional<Execution> childNexts(Flow flow, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException, InternalException {
        ResolvedTask parent = flow.findTaskByTaskRun(taskRun, new RunContext(this.applicationContext, flow, execution));

        if (parent.getTask() instanceof FlowableTask) {
            FlowableTask<?> flowableParent = (FlowableTask<?>) parent.getTask();
            List<TaskRun> nexts = flowableParent.resolveNexts(new RunContext(this.applicationContext, flow, execution), execution, taskRun);

            if (nexts.size() > 0) {
                return Optional.of(this.onNexts(flow, execution, nexts));
            }
        }

        return Optional.empty();
    }

    protected Execution onEnd(Flow flow, Execution execution) {
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
}
