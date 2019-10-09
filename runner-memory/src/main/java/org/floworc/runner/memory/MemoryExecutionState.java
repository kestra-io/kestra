package org.floworc.runner.memory;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.runners.ExecutionStateInterface;
import org.floworc.core.runners.WorkerTaskResult;
import org.floworc.core.utils.Await;

import javax.inject.Named;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Prototype
@MemoryQueueEnabled
public class MemoryExecutionState implements ExecutionStateInterface {
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
            synchronized (this) {
                if (execution.getState().isTerninated()) {
                    executions.remove(execution.getId());
                } else {
                    executions.put(execution.getId(), execution);
                }
            }
        });

        this.workerTaskResultQueue.receive(MemoryExecutionState.class, message -> {
            synchronized (this) {
                TaskRun taskRun = message.getTaskRun();

                if (!executions.containsKey(taskRun.getExecutionId())) {
                    throw new RuntimeException("Unable to find execution '" + taskRun.getExecutionId() + "' on ExecutionState");
                }

                // @FIXME: ugly hack, some time execution is not updated when the WorkerTaskResult is coming. We must sleep to wait that execution is updated.
                Await.until(() -> {
                    try {
                        Execution execution = executions.get(taskRun.getExecutionId());
                        Execution newExecution = execution.withTaskRun(taskRun);
                        this.executionQueue.emit(newExecution);
                        return true;
                    } catch (IllegalArgumentException e) {
                        log.warn("Execution is not updated yet, sleeping !", e);
                        return false;
                    }
                }, Duration.ofMillis(10));
            }
        });
    }
}
