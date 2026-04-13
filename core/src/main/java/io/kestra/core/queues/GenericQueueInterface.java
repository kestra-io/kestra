package io.kestra.core.queues;

import java.util.function.Consumer;

import io.kestra.core.queues.event.Event;

public interface GenericQueueInterface<T extends Event> extends AutoCloseable {
    /**
     * Add a listener to the queue.
     * This listener will receive every message emitted to the queue.
     * <p>
     * WARNING: the consumers will be called synchronously when emitted, this is designed to be used in tests or for
     * specific usage in low-volume queues.
     */
    void addListener(Consumer<T> listener);

    /**
     * Get the name of the queue
     */
    String queueName();
}
