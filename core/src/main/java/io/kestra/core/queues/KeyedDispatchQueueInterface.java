package io.kestra.core.queues;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KeyedDispatchQueueInterface<T extends io.kestra.core.queues.event.KeyedDispatchEvent> extends GenericQueueInterface<T> {
    void emit(String key, T message) throws QueueException;

    void emit(String key, List<T> messages) throws QueueException;

    CompletionStage<Void> emitAsync(String key, T message);

    CompletionStage<Void> emitAsync(String key, List<T> messages);

    QueueSubscriber<T> subscriber(String key);
}