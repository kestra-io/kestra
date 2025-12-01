package io.kestra.queue;

import io.kestra.core.queues.QueueException;

import java.util.List;
import java.util.Set;

public interface VNodeDispatchQueueInterface<T extends VNodeDispatchEvent> extends GenericQueueInterface<T> {
    void emit(T message) throws QueueException;

    void emit(List<T> messages) throws QueueException;

    QueueSubscriber<T> subscriber(Set<Integer> vNodes) throws QueueException;
}