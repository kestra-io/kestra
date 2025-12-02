package io.kestra.core.scheduler;

import io.kestra.core.utils.Disposable;
import io.kestra.core.scheduler.events.TriggerEvent;

import java.io.Closeable;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A service interface for publishing and subscribing to {@link TriggerEvent}s.
 */
public interface TriggerEventQueue extends Closeable {
    
    /**
     * Publishes a {@link TriggerEvent} to the queue.
     *
     * @param triggerEvent the event to publish.
     */
    void send(TriggerEvent triggerEvent);
    
    /**
     * Subscribes to events for the given virtual nodes.
     *
     * @param vNodes  the set of virtual node IDs to subscribe to.
     * @param consumer  the event consumer to invoke for each received batch of events; must not be {@code null}.
     * @return a {@link Disposable} used to stop the subscription.
     */
    Disposable subscribe(Set<Integer> vNodes, BatchRecordConsumer consumer);
    
    interface BatchRecordConsumer extends BiConsumer<Integer, List<TriggerEvent>> {
        
    }
}
