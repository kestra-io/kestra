package org.floworc.core.queues;

import java.util.function.Consumer;

public interface QueueInterface <T> {
    boolean emit(QueueMessage<T> message);

    void receive(Consumer<QueueMessage<T>> consumer);

    void ack(QueueMessage<T> message);
}
