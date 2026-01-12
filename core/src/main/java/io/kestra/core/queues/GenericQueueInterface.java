package io.kestra.core.queues;

import io.kestra.core.queues.event.Event;

import java.util.function.Consumer;

public interface GenericQueueInterface<T extends Event> {
    void addListener(Consumer<T> listener);
}
