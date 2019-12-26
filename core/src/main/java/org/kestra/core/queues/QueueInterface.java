package org.kestra.core.queues;

import java.io.Closeable;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable {
    void emit(T message);

    Runnable receive(Consumer<T> consumer);

    Runnable receive(Class consumerGroup, Consumer<T> consumer);
}
