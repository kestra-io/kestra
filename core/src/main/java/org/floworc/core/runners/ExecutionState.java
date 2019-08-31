package org.floworc.core.runners;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.executions.Execution;
import org.floworc.core.executions.TaskRun;
import org.floworc.core.executions.WorkerTask;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.queues.QueueMessage;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExecutionState implements Runnable {
    private final Object lock = new Object();
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<WorkerTask> workerTaskResultQueue;
    private static ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();

    public ExecutionState(
        QueueInterface<Execution> executionQueue,
        QueueInterface<WorkerTask> workerTaskResultQueue
    ) {
        this.executionQueue = executionQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.executionQueue.receive(message -> {
            synchronized (lock) {
                Execution execution = message.getBody();

                if (execution.getState().isTerninated()) {
                    executions.remove(message.getKey());
                } else {
                    executions.put(message.getKey(), execution);
                }
            }
        });

        this.workerTaskResultQueue.receive(message -> {
            synchronized (lock) {
                TaskRun taskRun = message.getBody().getTaskRun();

                if (!executions.containsKey(taskRun.getExecutionId())) {
                    throw new RuntimeException("Unable to find execution '" + taskRun.getExecutionId() + "' on ExecutionState");
                }

                Execution execution = executions.get(taskRun.getExecutionId());
                Execution newExecution = execution.withTaskRun(taskRun);

                this.executionQueue.emit(
                    QueueMessage.<Execution>builder()
                        .key(newExecution.getId())
                        .body(newExecution)
                        .build()
                );
            }
        });
    }
}
