package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.util.function.Consumer;

/*
 * Required for the QueueFactory, to have a common interface with JDBC & Kafka
 */
public interface WorkerTriggerResultQueueInterface extends Closeable, Pauseable {

    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer);
}
