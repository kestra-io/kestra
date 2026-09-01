package io.kestra.executor.testkit;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.KeyedDispatchEvent;

/**
 * In-memory {@link KeyedDispatchQueueInterface} that records every emitted message (with its
 * routing key) for assertions. Consumption is not supported.
 */
public class RecordingKeyedDispatchQueue<T extends KeyedDispatchEvent> implements KeyedDispatchQueueInterface<T> {
    public record Emitted<T>(String routingKey, T message) {
    }

    private final String name;
    private final List<Emitted<T>> emitted = new CopyOnWriteArrayList<>();

    public RecordingKeyedDispatchQueue(String name) {
        this.name = name;
    }

    @Override
    public void emit(String routingKey, T message) {
        emitted.add(new Emitted<>(routingKey, message));
    }

    @Override
    public void emit(String routingKey, List<T> messages) {
        messages.forEach(message -> emit(routingKey, message));
    }

    @Override
    public CompletionStage<Void> emitAsync(String routingKey, T message) {
        emit(routingKey, message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> emitAsync(String routingKey, List<T> messages) {
        emit(routingKey, messages);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public QueueSubscriber<T> subscriber(String routingKey) {
        throw new UnsupportedOperationException("RecordingKeyedDispatchQueue does not support subscriptions");
    }

    @Override
    public Integer queueLag(String routingKey) {
        return 0;
    }

    @Override
    public void addListener(Consumer<T> listener) {
        throw new UnsupportedOperationException("RecordingKeyedDispatchQueue does not support listeners");
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
    public List<Emitted<T>> emitted() {
        return List.copyOf(emitted);
    }

    /**
     * Every emitted message payload, in emission order.
     */
    public List<T> emittedMessages() {
        return emitted.stream().map(Emitted::message).toList();
    }
}
