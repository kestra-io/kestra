package io.kestra.scheduler;

import io.kestra.core.utils.Disposable;
import io.kestra.scheduler.events.SchedulerEvent;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Represents a queue for publishing and subscribing to {@link SchedulerEvent} instances.
 */
public interface SchedulerEventQueue extends Closeable {
    
    /**
     * Publishes a {@link SchedulerEvent} to the queue.
     * <p>
     * The event will be asynchronously delivered to all active subscribers.
     * </p>
     *
     * @param event the event to be published; must not be {@code null}.
     * @throws IllegalStateException if the queue has been closed or is not accepting events.
     */
    void send(SchedulerEvent event);
    
    /**
     * Subscribes a consumer to receive {@link SchedulerEvent} instances from the queue.
     * <p>
     * The provided consumer will be invoked for each event published after subscription.
     * Implementations may deliver events on a background thread or using reactive streams.
     * </p>
     *
     * @param consumer the event consumer to invoke for each received event; must not be {@code null}.
     * @return a {@link Disposable} that can be used to cancel the subscription.
     */
    Disposable subscribe(Consumer<SchedulerEvent> consumer);
}
