package io.kestra.core.queues;

import java.io.Closeable;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable {
    void emit(T message) throws QueueException;

    void delete(T message) throws QueueException;

    Runnable receive(Consumer<T> consumer);

    Runnable receive(Class<?> consumerGroup, Consumer<T> consumer);
}
