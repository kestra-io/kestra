package io.kestra.core.queues;

import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;

/**
 * A subscriber for consuming messages from a queue.
 * <p>
 * This interface provides methods to subscribe to a queue, pause and resume message consumption,
 * and close the subscriber when no longer needed.
 *
 * @param <T> the type of event this subscriber handles
 */
public interface QueueSubscriber<T extends Event> {
    /**
     * Starts a subscription to consume messages one by one from the queue.
     *
     * @param consumer the consumer that will process messages; receives either a successfully
     *        deserialized event or a {@link DeserializationException}
     * @return this subscriber instance for method chaining
     *
     * @see #subscribeBatch(Consumer)
     */
    QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer);

    /**
     * Starts a subscription to consume messages in batch from the queue.
     *
     * @param consumer the consumer that will process messages; receives a list of either a successfully
     *        deserialized event or a {@link DeserializationException}
     * @return this subscriber instance for method chaining
     *
     * @see #subscribe(Consumer)
     */
    default QueueSubscriber<T> subscribeBatch(Consumer<List<Either<T, DeserializationException>>> consumer) {
        return subscribe(either -> consumer.accept(List.of(either)));
    }

    /**
     * Pauses this subscriber, temporarily stopping message consumption.
     * <p>
     * This method is idempotent: calling it when already paused has no effect.
     * Messages will not be consumed until {@link #resume()} is called.
     */
    void pause();

    /**
     * Resumes this subscriber if currently paused.
     * <p>
     * This method is idempotent: calling it when already running has no effect.
     */
    void resume();

    /**
     * Closes this subscriber and releases any associated resources.
     * <p>
     * If the subscriber is paused, it will be resumed before closing to allow
     * any blocked threads to complete. This method blocks until the subscriber
     * has fully stopped.
     */
    void close();
}
