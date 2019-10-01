package org.floworc.runner.memory;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.runners.ExecutionStateInterface;
import org.floworc.core.runners.WorkerTaskResult;

import javax.inject.Named;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Prototype
@MemoryQueueEnabled
public class MemoryExecutionState implements ExecutionStateInterface {
    private final Object lock = new Object();
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private static ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();

    public MemoryExecutionState(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        this.executionQueue = executionQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.executionQueue.receive(MemoryExecutionState.class, execution -> {
            synchronized (lock) {
                if (execution.getState().isTerninated()) {
                    executions.remove(execution.getId());
                } else {
                    executions.put(execution.getId(), execution);
                }
            }
        });

        this.workerTaskResultQueue.receive(MemoryExecutionState.class, message -> {
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
