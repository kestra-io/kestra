package io.kestra.core.queues;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.kestra.core.queues.event.BroadcastEvent;

public interface BroadcastQueueInterface<T extends BroadcastEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    CompletionStage<Void> emitAsync(T message);

    CompletionStage<Void> emitAsync(List<T> messages);

    QueueSubscriber<T> subscriber();
}