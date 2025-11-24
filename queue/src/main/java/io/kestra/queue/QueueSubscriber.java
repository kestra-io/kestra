package io.kestra.queue;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.Rethrow;

public interface QueueSubscriber<T extends Event> {
    /**
     * Start a subscription.
     *
     * @param consumer the consumer that will process messages
     * @return self
     * @throws QueueException if subscription fails
     */
    QueueSubscriber<T> subscribe(Rethrow.ConsumerChecked<Either<T, DeserializationException>, Exception> consumer) throws QueueException;

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
