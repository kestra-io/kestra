package io.kestra.core.queues;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KeyedDispatchQueueInterface<T extends io.kestra.core.queues.event.KeyedDispatchEvent> extends GenericQueueInterface<T> {
    void emit(String routingKey, T message) throws QueueException;

    void emit(String routingKey, List<T> messages) throws QueueException;

    CompletionStage<Void> emitAsync(String routingKey, T message);

    CompletionStage<Void> emitAsync(String routingKey, List<T> messages);

    QueueSubscriber<T> subscriber(String routingKey);

    Integer queueLag(String routingKey);
}