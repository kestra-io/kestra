package io.kestra.core.queues;

import io.kestra.core.queues.event.DispatchEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DispatchQueueInterface <T extends DispatchEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    CompletionStage<Void> emitAsync(T message);

    CompletionStage<Void> emitAsync(List<T> messages);

    QueueSubscriber<T> subscriber();
}