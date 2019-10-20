package org.floworc.core.runners;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Executor implements Runnable {
    private QueueInterface<Execution> executionQueue;
    private QueueInterface<WorkerTask> workerTaskQueue;
    private FlowRepositoryInterface flowRepository;
    private ExecutionService executionService;

    public Executor(
        QueueInterface<Execution> executionQueue,
        QueueInterface<WorkerTask> workerTaskQueue,
        FlowRepositoryInterface flowRepositoryInterface,
        ExecutionService executionService
    ) {
        this.executionQueue = executionQueue;
        this.workerTaskQueue = workerTaskQueue;
        this.flowRepository = flowRepositoryInterface;
        this.executionService = executionService;
    }

    @Override
    public void run() {
        this.executionQueue.receive(Executor.class, execution -> {
            if (execution.getState().isTerninated()) {
                return;
            }

            Flow flow = this.flowRepository
                .findById(execution.getFlowId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));

            this.executionService.getNexts(execution, flow.getTasks())
                .ifPresentOrElse(
                    nexts -> this.onNexts(flow, execution, nexts),
                    () -> this.onEnd(flow, execution)
                );
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

        // submit TaskRun
        for (TaskRun taskRun: nexts) {
            Task task = flow.findTaskById(taskRun.getTaskId());

            this.workerTaskQueue.emit(
                WorkerTask.builder()
                    .runContext(new RunContext(flow, task, execution, taskRun))
                    .taskRun(taskRun)
                    .task(task)
                    .build()
            );
        }
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
