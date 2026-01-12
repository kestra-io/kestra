package io.kestra.core.queues;

import io.kestra.core.queues.event.VNodeDispatchEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface VNodeDispatchQueueInterface<T extends VNodeDispatchEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    CompletionStage<Void> emitAsync(T message);

    CompletionStage<Void> emitAsync(List<T> messages);

    QueueSubscriber<T> subscriber(Set<Integer> vNodes);
}