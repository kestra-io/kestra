package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;

import java.util.function.Consumer;

public interface QueueSubscriber<T extends Event> {
    /**
     * Start a subscription.
     *
     * @param consumer the consumer that will process messages
     * @return self
     */
    QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer);

    /**
     * Pauses this subscriber.
     */
    void pause();

    /**
     * Resumes this subscriber if currently paused.
     */
    void resume();

    /**
     * close this subscriber.
     */
    void close();
}
