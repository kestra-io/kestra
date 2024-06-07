package io.kestra.runner.memory;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Either;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.WorkerJob;

import java.util.function.Consumer;

public class MemoryWorkerJobQueue implements WorkerJobQueueInterface {
    QueueInterface<WorkerJob> workerTaskQueue;

    @SuppressWarnings("unchecked")
    public MemoryWorkerJobQueue(ApplicationContext applicationContext) {
        this.workerTaskQueue = (QueueInterface<WorkerJob>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED)
        );
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return workerTaskQueue.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void close() {

    }
}
