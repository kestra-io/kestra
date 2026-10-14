package io.kestra.executor.testkit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;

/**
 * In-memory queue that records what is emitted and holds it until a test delivers it, one message
 * at a time, to the consumer the executor subscribed. Emitting never delivers inline: that is what
 * keeps production's "emit after commit" ordering observable without threads.
 */
abstract class RecordingQueue<T extends Event> implements QueueSubscriber<T> {
    private final String name;
    private final List<Trace.Emission> journal;
    private final List<T> emitted = new ArrayList<>();
    private final Deque<T> pending = new ArrayDeque<>();
    private Consumer<Either<T, DeserializationException>> consumer;

    RecordingQueue(String name, List<Trace.Emission> journal) {
        this.name = name;
        this.journal = journal;
    }

    public void emit(T message) {
        emitted.add(message);
        pending.add(message);
        journal.add(new Trace.Emission(journal.size(), name, message));
    }

    public void emit(List<T> messages) {
        messages.forEach(this::emit);
    }

    public CompletionStage<Void> emitAsync(T message) {
        emit(message);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> emitAsync(List<T> messages) {
        emit(messages);
        return CompletableFuture.completedFuture(null);
    }

    public QueueSubscriber<T> subscriber() {
        return this;
    }

    public void addListener(Consumer<T> listener) {
        throw new UnsupportedOperationException("listeners are not supported by the testkit");
    }

    public String queueName() {
        return name;
    }

    public List<T> emitted() {
        return List.copyOf(emitted);
    }

    boolean isSubscribed() {
        return consumer != null;
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    Object peekPending() {
        return pending.peek();
    }

    void deliverNext() {
        if (consumer == null) {
            throw new IllegalStateException("nothing subscribed to queue '" + name + "'");
        }
        consumer.accept(Either.left(pending.poll()));
    }

    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        this.consumer = consumer;
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
