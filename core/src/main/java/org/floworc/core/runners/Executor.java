package org.floworc.core.runners;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.FlowableResult;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Executor implements Runnable {
    private QueueInterface<Execution> executionQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private FlowRepositoryInterface flowRepository;

    public Executor(
        QueueInterface<Execution> executionQueue,
        QueueInterface<WorkerTaskResult> workerTaskResultQueue,
        FlowRepositoryInterface flowRepositoryInterface
    ) {
        this.executionQueue = executionQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.flowRepository = flowRepositoryInterface;
    }

    @Override
    public void run() {
        this.executionQueue.receive(Executor.class, execution -> {
            if (execution.getState().isTerninated()) {
                return;
            }

            Flow flow = this.flowRepository
                .findByExecution(execution)
                .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));


            FlowableResult result = FlowableUtils.getNexts(new RunContext(flow, execution), execution, flow.getTasks(), flow.getErrors());

            // trigger next taskRuns
            if (result.getResult() == FlowableResult.Result.NEXTS) {
                this.onNexts(flow, execution, result.getNexts());
            }

            // something ended
            if (result.getResult() == FlowableResult.Result.ENDED) {
                if (result.getChildTaskRun() != null) {
                    // childs tasks ended, trigger parent task result
                    this.workerTaskResultQueue.emit(new WorkerTaskResult(
                        result.getChildTaskRun().withState(result.getChildState()),
                        result.getChildTask()
                    ));
                } else {
                    // flow ended, terminate it
                    this.onEnd(flow, execution);
                }
            }
        });
    }

    private void onNexts(Flow flow, Execution execution, List<TaskRun> nexts) {
        if (nexts.size() == 0) {
            flow.logger().trace(
                "[execution: {}] Call getNexts but no next found: {}",
                execution.getId(),
                execution
            );
            return;
        } else {
            flow.logger().trace(
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

        this.executionQueue.emit(newExecution);
    }

    private void onEnd(Flow flow, Execution execution) {
        Execution newExecution = execution.withState(
            execution.hasFailed() ? State.Type.FAILED : State.Type.SUCCESS
        );

        flow.logger().info(
            "[execution: {}] Flow completed with state {} in {}",
            newExecution.getId(),
            newExecution.getState().getCurrent(),
            newExecution.getState().humanDuration()
        );

        this.executionQueue.emit(newExecution);
    }
}
