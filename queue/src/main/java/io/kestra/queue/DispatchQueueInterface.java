package io.kestra.queue;

import io.kestra.core.queues.QueueException;

import java.util.List;

public interface DispatchQueueInterface <T extends DispatchEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    QueueSubscriber<T> subscriber() throws QueueException;
}