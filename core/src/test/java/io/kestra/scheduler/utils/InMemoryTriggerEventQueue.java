package io.kestra.scheduler.utils;

import io.kestra.scheduler.vnodes.VNodes;
import io.kestra.core.utils.Disposable;
import io.kestra.scheduler.TriggerEventQueue;
import io.kestra.scheduler.events.TriggerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Simple in-memory event queue implementation.
 * <p>
 * This class is for testing-purpose only.
 */
public class InMemoryTriggerEventQueue implements TriggerEventQueue {
    
    private final List<BatchRecordConsumer> subscribers = new ArrayList<>();
    private final List<TriggerEvent> sentEvents = new ArrayList<>();
    
    private final int vNodes;
    
    /**
     * Creates a new {@link InMemoryTriggerEventQueue} instance.
     * @param vNodes    the number of vNodes.
     */
    public InMemoryTriggerEventQueue(int vNodes) {
        this.vNodes = vNodes;
    }
    
    
    /** {@inheritDoc} **/
    @Override
    public void send(TriggerEvent event) {
        sentEvents.add(event);
        for (BatchRecordConsumer subscriber : subscribers) {
            subscriber.accept(VNodes.computeVNodeFromTrigger(event.id(), vNodes), List.of(event));
        }
    }
    
    /** {@inheritDoc} **/
    @Override
    public Disposable subscribe(Set<Integer> vNodes, BatchRecordConsumer consumer) {
        subscribers.add(consumer);
        return Disposable.of(() -> subscribers.remove(consumer));
    }
    
    /** {@inheritDoc} **/
    @Override
    public void close() {
        subscribers.clear();
    }
    
    public List<TriggerEvent> sentEvents() {
        return sentEvents;
    }
    
    public List<BatchRecordConsumer> subscribers() {
        return subscribers;
    }
}
