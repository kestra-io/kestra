package org.floworc.core.runners;

import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.storages.StorageInterface;

public class Worker implements Runnable {
    private StorageInterface storageInterface;
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    public Worker(
        StorageInterface storageInterface,
        QueueInterface<WorkerTask> workerTaskQueue,
        QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        this.storageInterface = storageInterface;
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

            // run
            RunnableTask task = (RunnableTask) workerTask.getTask();

            RunContext runContext = workerTask
                .getRunContext()
                .withStorageInterface(this.storageInterface);

            RunOutput output = null;

            try {
                output = task.run(runContext);
                state = State.Type.SUCCESS;
            } catch (Exception e) {
                workerTask.logger().error("Failed task", e);
                state = State.Type.FAILED;
            }

            // save outputs
            workerTask = workerTask
                .withTaskRun(
                    workerTask.getTaskRun()
                        .withLogs(runContext.logs())
                        .withMetrics(runContext.metrics())
                        .withOutputs(output != null ? output.getOutputs() : null)
                );

            // emit
            this.workerTaskResultQueue.emit(
                new WorkerTaskResult(workerTask, state)
            );

            // log
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
