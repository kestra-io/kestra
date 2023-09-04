package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable {
    default void emit(T message) throws QueueException {
        emit(null, message);
    }

    void emit(String consumerGroup, T message) throws QueueException;

    default void emitAsync(T message) throws QueueException {
        emitAsync(null, message);
    }

    void emitAsync(String consumerGroup, T message) throws QueueException;

    default void delete(T message) throws QueueException {
        delete(null, message);
    }

    void delete(String consumerGroup, T message) throws QueueException;

    default Runnable receive(Consumer<Either<T, DeserializationException>> consumer) {
        return receive((String) null, consumer);
    }

    Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer);

    default Runnable receive(Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return receive(null, queueType, consumer);
    }

    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer);

    void pause();
}
