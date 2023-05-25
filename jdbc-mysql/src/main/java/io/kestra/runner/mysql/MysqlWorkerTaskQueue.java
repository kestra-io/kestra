package io.kestra.runner.mysql;

import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTaskQueueInterface;
import io.kestra.core.runners.WorkerTask;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.function.Consumer;

@Singleton
@MysqlQueueEnabled
public class MysqlWorkerTaskQueue implements WorkerTaskQueueInterface {
    QueueInterface<WorkerTask> workerTaskQueue;

    @SuppressWarnings("unchecked")
    public MysqlWorkerTaskQueue(ApplicationContext applicationContext) {
        this.workerTaskQueue = (QueueInterface<WorkerTask>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASK_NAMED)
        );
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<WorkerTask> consumer) {
        return workerTaskQueue.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void pause() {

    }

    @Override
    public void close() {

    }
}
