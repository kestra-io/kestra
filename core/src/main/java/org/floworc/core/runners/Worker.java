package org.floworc.core.runners;

import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.queues.QueueInterface;

public class Worker implements Runnable {
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    public Worker(QueueInterface<WorkerTask> workerTaskQueue, QueueInterface<WorkerTaskResult> workerTaskResultQueue) {
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.workerTaskQueue.receive(Worker.class, this::run);
    }

    public void run(WorkerTask workerTask) {
        workerTask.logger().info(
            "[execution: {}] [taskrun: {}] Task {} started",
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTask().getClass().getSimpleName()
        );

        this.workerTaskResultQueue.emit(
            new WorkerTaskResult(workerTask, State.Type.RUNNING)
        );

        if (workerTask.getTask() instanceof RunnableTask) {
            State.Type state;

            RunnableTask task = (RunnableTask) workerTask.getTask();
            try {
                task.run();
                state = State.Type.SUCCESS;
            } catch (Exception e) {
                workerTask.logger().error("Failed task", e);
                state = State.Type.FAILED;
            }

            this.workerTaskResultQueue.emit(
                new WorkerTaskResult(workerTask, state)
            );

            workerTask.logger().info(
                "[execution: {}] [taskrun: {}] Task {} with state {} completed in {}",
                workerTask.getTaskRun().getExecutionId(),
                workerTask.getTaskRun().getId(),
                workerTask.getTask().getClass().getSimpleName(),
                state,
                workerTask.getTaskRun().getState().humanDuration()
            );
        }
    }
}
