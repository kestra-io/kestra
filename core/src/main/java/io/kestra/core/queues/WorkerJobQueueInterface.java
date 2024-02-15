package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.util.function.Consumer;

public interface WorkerJobQueueInterface extends Closeable {
    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer);

    void pause();

}
