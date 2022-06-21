package io.kestra.runner.h2;

import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTaskQueueInterface;
import io.kestra.core.runners.WorkerTask;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.function.Consumer;

@Singleton
@H2QueueEnabled
public class H2WorkerTaskQueue implements WorkerTaskQueueInterface {
    QueueInterface<WorkerTask> workerTaskQueue;

    @SuppressWarnings("unchecked")
    public H2WorkerTaskQueue(ApplicationContext applicationContext) {
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
    public void pause() {

    }

    @Override
    public void close() {

    }
}
