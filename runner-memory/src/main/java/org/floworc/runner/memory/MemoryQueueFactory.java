package org.floworc.runner.memory;

import io.micronaut.context.annotation.Factory;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.runners.*;

import javax.inject.Named;
import javax.inject.Singleton;

@Factory
public class MemoryQueueFactory implements QueueFactoryInterface {
    @Singleton
    @Named("executionQueue")
    public QueueInterface<Execution> execution() {
        return new MemoryQueue<>(Execution.class);
    }

    @Singleton
    @Named("workerTaskQueue")
    public QueueInterface<WorkerTask> workerTask() {
        return new MemoryQueue<>(WorkerTask.class);
    }

    @Singleton
    @Named("workerTaskResultQueue")
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new MemoryQueue<>(WorkerTaskResult.class);
    }
}
