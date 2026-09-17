package io.kestra.executor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.queues.event.Event;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.utils.Either;

/**
 * In-memory fake queues (Roman-style): every emit is recorded in {@link QueueRecorder}, nothing is
 * consumed, no threads. The subscriber is a no-op so any startup bean that subscribes does nothing.
 */
final class NoopQueueSubscriber<T extends Event> implements QueueSubscriber<T> {
    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        return this;
    }

    @Override
    public void pause() {
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void resume() {
    }

    @Override
    public void close() {
    }
}

final class RecordingDispatchQueue<T extends DispatchEvent> implements DispatchQueueInterface<T> {
    private final Class<T> type;
    private final QueueRecorder recorder;

    RecordingDispatchQueue(Class<T> type, QueueRecorder recorder) {
        this.type = type;
        this.recorder = recorder;
    }

    @Override
    public void emit(T message) {
        recorder.record(type, message);
    }

    @Override
    public void emit(List<T> messages) {
        messages.forEach(message -> recorder.record(type, message));
    }

    @Override
    public CompletionStage<Void> emitAsync(T message) {
        recorder.record(type, message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> emitAsync(List<T> messages) {
        messages.forEach(message -> recorder.record(type, message));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new NoopQueueSubscriber<>();
    }

    @Override
    public void addListener(Consumer<T> listener) {
    }

    @Override
    public String queueName() {
        return type.getSimpleName();
    }

    @Override
    public void close() {
    }
}

final class RecordingKeyedDispatchQueue<T extends KeyedDispatchEvent> implements KeyedDispatchQueueInterface<T> {
    private final Class<T> type;
    private final QueueRecorder recorder;

    RecordingKeyedDispatchQueue(Class<T> type, QueueRecorder recorder) {
        this.type = type;
        this.recorder = recorder;
    }

    @Override
    public void emit(String routingKey, T message) {
        recorder.record(type, message);
    }

    @Override
    public void emit(String routingKey, List<T> messages) {
        messages.forEach(message -> recorder.record(type, message));
    }

    @Override
    public CompletionStage<Void> emitAsync(String routingKey, T message) {
        recorder.record(type, message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> emitAsync(String routingKey, List<T> messages) {
        messages.forEach(message -> recorder.record(type, message));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public QueueSubscriber<T> subscriber(String routingKey) {
        return new NoopQueueSubscriber<>();
    }

    @Override
    public Integer queueLag(String routingKey) {
        return 0;
    }

    @Override
    public void addListener(Consumer<T> listener) {
    }

    @Override
    public String queueName() {
        return type.getSimpleName();
    }

    @Override
    public void close() {
    }
}
