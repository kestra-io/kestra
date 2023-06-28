package io.kestra.core.queues;

import io.kestra.core.runners.WorkerJob;

import java.io.Closeable;
import java.util.function.Consumer;

public interface WorkerJobQueueInterface extends Closeable {
    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<WorkerJob> consumer);

    void pause();
}
