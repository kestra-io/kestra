package io.kestra.core.queues;

import io.kestra.core.runners.WorkerTask;

import java.io.Closeable;
import java.util.function.Consumer;

public interface WorkerTaskQueueInterface extends Closeable {
    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<WorkerTask> consumer);

    void pause();
}
