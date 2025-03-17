package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Interface for consuming the {@link WorkerJob} queue.
 */
public interface WorkerJobQueueInterface extends Closeable, Pauseable {

    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer);

    /**
     * Closes any resources used for the queue consumption.
     */
    @Override
    void close() throws IOException;
}
