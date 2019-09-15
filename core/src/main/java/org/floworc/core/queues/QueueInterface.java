package org.floworc.core.queues;

import java.util.function.Consumer;

public interface QueueInterface<T> {
    void emit(T message);

    void receive(Class consumerGroup, Consumer<T> consumer);

    void ack(T message);
}
