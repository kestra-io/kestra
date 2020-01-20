package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public abstract class AbstractExecutor implements Runnable {
    protected ApplicationContext applicationContext;

    public AbstractExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    protected Execution onNexts(Flow flow, Execution execution, List<TaskRun> nexts) {
        if (log.isTraceEnabled()) {
            log.trace(
                "[execution: {}] Found {} next(s) {}",
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
            flow.logger().info(
                "[execution: {}] Flow started",
                execution.getId()
            );

            newExecution = newExecution.withState(State.Type.RUNNING);
        }

        return newExecution;
    }

    protected Optional<WorkerTaskResult> childWorkerTaskResult(Flow flow, Execution execution, TaskRun taskRun) {
        RunContext runContext = new RunContext(this.applicationContext, flow, execution);
        ResolvedTask parent = flow.findTaskByTaskRun(taskRun, runContext);

        if (parent.getTask() instanceof FlowableTask) {
            FlowableTask flowableParent = (FlowableTask) parent.getTask();

            return flowableParent
                .resolveState(runContext, execution, taskRun)
                .map(type -> new WorkerTaskResult(
                    taskRun
                        .withState(type)
                        .withOutputs(flowableParent.outputs(runContext, execution, taskRun)),
                    parent.getTask()
                ));

        }

        return Optional.empty();
    }

    protected Optional<Execution> childNexts(Flow flow, Execution execution, TaskRun taskRun) {
        ResolvedTask parent = flow.findTaskByTaskRun(taskRun, new RunContext(this.applicationContext, flow, execution));

        if (parent.getTask() instanceof FlowableTask) {
            FlowableTask flowableParent = (FlowableTask) parent.getTask();
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
            "[execution: {}] Flow completed with state {} in {}",
            newExecution.getId(),
            newExecution.getState().getCurrent(),
            newExecution.getState().humanDuration()
        );

        if (logger.isTraceEnabled()) {
            logger.debug(execution.toString(true));
        }

        return newExecution;
    }
}
