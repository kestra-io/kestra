package io.kestra.executor.testkit;

import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;

/**
 * Captures the consumer the executor registers in {@code doRun()}; delivery is driven by the test.
 */
final class RecordingQueueSubscriber<T extends Event> implements QueueSubscriber<T> {
    private final Consumer<Consumer<Either<T, DeserializationException>>> onSubscribe;

    RecordingQueueSubscriber(Consumer<Consumer<Either<T, DeserializationException>>> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        onSubscribe.accept(consumer);
        return this;
    }

    @Override
    public QueueSubscriber<T> subscribeBatch(Consumer<List<Either<T, DeserializationException>>> consumer) {
        return subscribe(either -> consumer.accept(List.of(either)));
    }

    @Override
    public void pause() {
        // delivery is explicit in the testkit
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void resume() {
        // delivery is explicit in the testkit
    }

    @Override
    public void close() {
        // nothing to close
    }
}
