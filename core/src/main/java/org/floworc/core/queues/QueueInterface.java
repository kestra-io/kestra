package org.floworc.core.queues;

import java.io.Closeable;
import java.util.function.Consumer;

public interface QueueInterface<T> extends Closeable {
    void emit(T message);

    void receive(Class consumerGroup, Consumer<T> consumer);

    void ack(T message);
}
