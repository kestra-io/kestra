package org.kestra.core.queues;

import org.kestra.core.runners.WorkerTask;

import java.io.Closeable;
import java.util.function.Consumer;

public interface WorkerTaskQueueInterface extends Closeable {
    Runnable receive(Class<?> consumerGroup, Consumer<WorkerTask> consumer);
}
