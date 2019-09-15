package org.floworc.core.runners;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.queues.QueueInterface;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExecutionState implements Runnable {
    private final Object lock = new Object();
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private static ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();

    public ExecutionState(
        QueueInterface<Execution> executionQueue,
        QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        this.executionQueue = executionQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.executionQueue.receive(ExecutionState.class, execution -> {
            synchronized (lock) {
                if (execution.getState().isTerninated()) {
                    executions.remove(execution.getId());
                } else {
                    executions.put(execution.getId(), execution);
                }
            }
        });

        this.workerTaskResultQueue.receive(ExecutionState.class, message -> {
            synchronized (lock) {
                TaskRun taskRun = message.getTaskRun();

                if (!executions.containsKey(taskRun.getExecutionId())) {
                    throw new RuntimeException("Unable to find execution '" + taskRun.getExecutionId() + "' on ExecutionState");
                }

                Execution execution = executions.get(taskRun.getExecutionId());
                Execution newExecution = execution.withTaskRun(taskRun);

                this.executionQueue.emit(newExecution);
            }
        });
    }
}
