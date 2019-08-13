package org.floworc.core.runners;

import org.floworc.core.executions.WorkerTask;
import org.floworc.core.flows.State;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.queues.QueueMessage;

public class Worker implements Runnable {
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTask> workerTaskResultQueue;

    public Worker(QueueInterface<WorkerTask> workerTaskQueue, QueueInterface<WorkerTask> workerTaskResultQueue) {
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.workerTaskQueue.receive(message -> {
            this.run(message.getBody());
        });
    }

    public void run(WorkerTask workerTask) {
        workerTask.logger().info(
            "[execution: {}] [taskrun: {}] Task started",
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId()
        );

        this.workerTaskResultQueue.emit(QueueMessage.<WorkerTask>builder()
            .key(workerTask.getTaskRun().getExecutionId())
            .body(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING)))
            .build()
        );

        try {
            workerTask.getTask().run();

            this.workerTaskResultQueue.emit(QueueMessage.<WorkerTask>builder()
                .key(workerTask.getTaskRun().getExecutionId())
                .body(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.SUCCESS)))
                .build()
            );
        } catch (Exception e) {
            workerTask.logger().error("Failed task", e);

            this.workerTaskResultQueue.emit(QueueMessage.<WorkerTask>builder()
                .key(workerTask.getTaskRun().getExecutionId())
                .body(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.FAILED)))
                .build()
            );
        }

        workerTask.logger().info(
            "[execution: {}] [taskrun: {}] Task completed in {}",
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTaskRun().getState().humanDuration()
        );
    }
}
