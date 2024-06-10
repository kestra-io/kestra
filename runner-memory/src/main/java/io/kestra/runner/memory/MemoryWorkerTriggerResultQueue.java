package io.kestra.runner.memory;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.Either;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.function.Consumer;

public class MemoryWorkerTriggerResultQueue implements WorkerTriggerResultQueueInterface {
    QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    @SuppressWarnings("unchecked")
    public MemoryWorkerTriggerResultQueue(ApplicationContext applicationContext) {
        this.workerTriggerResultQueue = (QueueInterface<WorkerTriggerResult>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
        );
    }

    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer) {
        return workerTriggerResultQueue.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void close() {

    }
}
