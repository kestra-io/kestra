package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.utils.Either;

import java.io.Closeable;
import java.util.List;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable, Pauseable {
    default void emit(T message) throws QueueException {
        emit(null, message);
    }

    void emit(String consumerGroup, T message) throws QueueException;

    default void emitAsync(T message) throws QueueException {
        emitAsync(null, message);
    }

    default void emitAsync(String consumerGroup, T message) throws QueueException {
        emitAsync(consumerGroup, List.of(message));
    }

    default void emitAsync(List<T> messages) throws QueueException {
        emitAsync(null, messages);
    }

    void emitAsync(String consumerGroup, List<T> messages) throws QueueException;

    default void delete(T message) throws QueueException {
        delete(null, message);
    }

    void delete(String consumerGroup, T message) throws QueueException;

    /**
     * Delete all messages of the queue for this key.
     * This is used to purge a queue for a specific key.
     * A queue implementation may omit to implement it and purge records differently.
     */
    default void deleteByKey(String key) throws QueueException {
        // by default do nothing
    }

    /**
     * Delete all messages of the queue for a set of keys.
     * This is used to purge a queue for specific keys.
     * A queue implementation may omit to implement it and purge records differently.
     */
    default void deleteByKeys(List<String> keys) throws QueueException {
        // by default do nothing
    }

    default Runnable receive(Consumer<Either<T, DeserializationException>> consumer) {
        return receive(null, consumer, false);
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

    default Runnable receiveBatch(Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer) {
        return receiveBatch(null, queueType, consumer);
    }

    default Runnable receiveBatch(String consumerGroup, Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer) {
        return receiveBatch(consumerGroup, queueType, consumer, true);
    }

    /**
     * Consumer a batch of messages.
     * By default, it consumes a single message, a queue implementation may implement it to support batch consumption.
     */
    default Runnable receiveBatch(String consumerGroup, Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer, boolean forUpdate) {
        return receive(consumerGroup, either -> consumer.accept(List.of(either)), forUpdate);
    }
}
