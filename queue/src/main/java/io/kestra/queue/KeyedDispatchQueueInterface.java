package io.kestra.queue;

import io.kestra.core.queues.QueueException;

import java.util.List;

public interface KeyedDispatchQueueInterface<T extends KeyedDispatchEvent> extends GenericQueueInterface<T> {
    void emit(String key, T message) throws QueueException;

    void emit(String key, List<T> messages) throws QueueException;

    QueueSubscriber<T> subscriber(String key) throws QueueException;
}