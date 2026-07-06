package io.kestra.executor.testkit;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.DispatchEvent;

/**
 * In-memory {@link DispatchQueueInterface} that records every emitted message for assertions.
 * Consumption is not supported: the testkit never starts queue subscriptions.
 */
public class RecordingDispatchQueue<T extends DispatchEvent> implements DispatchQueueInterface<T> {
    private final String name;
    private final List<T> emitted = new CopyOnWriteArrayList<>();

    public RecordingDispatchQueue(String name) {
        this.name = name;
    }

    @Override
    public void emit(T message) {
        emitted.add(message);
    }

    @Override
    public void emit(List<T> messages) {
        emitted.addAll(messages);
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
        throw new UnsupportedOperationException("RecordingDispatchQueue does not support subscriptions");
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
}
