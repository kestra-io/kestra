package org.kestra.runner.memory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.queues.WorkerTaskQueueInterface;
import org.kestra.core.runners.WorkerTask;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
@MemoryQueueEnabled
public class MemoryWorkerTaskQueue implements WorkerTaskQueueInterface {
    QueueInterface<WorkerTask> workerTaskQueue;

    @SuppressWarnings("unchecked")
    public MemoryWorkerTaskQueue(ApplicationContext applicationContext) {
        this.workerTaskQueue = (QueueInterface<WorkerTask>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASK_NAMED)
        );
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<WorkerTask> consumer) {
        return workerTaskQueue.receive(consumerGroup, consumer);
    }

    @Override
    public void close() {

    }
}
