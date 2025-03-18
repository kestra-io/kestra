package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable, Pauseable {
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

    default Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer) {
        return receive(consumerGroup, consumer, true);
    }

    Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer, boolean forUpdate);

    default Runnable receive(Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return receive(null, queueType, consumer);
    }

    default Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return receive(consumerGroup, queueType, consumer, true);
    }

    Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer, boolean forUpdate);
}
