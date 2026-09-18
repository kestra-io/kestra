package io.kestra.executor.testkit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.Either;

/**
 * In-memory {@link DispatchQueueInterface} that records every emitted message for assertions.
 * Consumption is not supported: the testkit never starts queue subscriptions.
 */
public class RecordingDispatchQueue<T extends DispatchEvent> implements DispatchQueueInterface<T>, Deliverable {
    private final String name;
    private final EmissionJournal journal;
    private final Deque<T> pending = new ArrayDeque<>();
    private Consumer<Either<T, DeserializationException>> consumer;
    private final List<T> emitted = new CopyOnWriteArrayList<>();

    public RecordingDispatchQueue(String name) {
        this(name, new EmissionJournal());
    }

    public RecordingDispatchQueue(String name, EmissionJournal journal) {
        this.name = name;
        this.journal = journal;
    }

    @Override
    public void emit(T message) {
        emitted.add(message);
        pending.add(message);
        journal.record(name, message);
    }

    @Override
    public void emit(List<T> messages) {
        messages.forEach(this::emit);
    }

    @Override
    public CompletionStage<Void> emitAsync(T message) {
        emit(message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> emitAsync(List<T> messages) {
        emit(messages);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new RecordingQueueSubscriber<>(c -> this.consumer = c);
    }

    @Override
    public void addListener(Consumer<T> listener) {
        throw new UnsupportedOperationException("RecordingDispatchQueue does not support listeners");
    }

    @Override
    public String queueName() {
        return name;
    }

    @Override
    public void close() {
        // nothing to close
    }

    /**
     * Every message emitted to this queue, in emission order.
     */
    public List<T> emitted() {
        return List.copyOf(emitted);
    }

    @Override
    public boolean isSubscribed() {
        return consumer != null;
    }

    @Override
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    @Override
    public Object peekPending() {
        return pending.peek();
    }

    /**
     * Hands the oldest pending message to the subscriber the executor registered — one production
     * delivery, on the calling thread.
     */
    @Override
    public void deliverNext() {
        if (consumer == null) {
            throw new IllegalStateException("nothing subscribed to queue '" + name + "'");
        }
        consumer.accept(Either.left(pending.poll()));
    }
}
