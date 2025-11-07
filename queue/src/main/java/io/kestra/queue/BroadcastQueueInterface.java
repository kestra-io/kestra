package io.kestra.queue;

import io.kestra.core.queues.QueueException;

import java.util.List;

public interface BroadcastQueueInterface <T extends BroadcastEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    QueueSubscriber<T> subscriber() throws QueueException;
}