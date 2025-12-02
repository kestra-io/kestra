package io.kestra.scheduler.utils;

import io.kestra.core.utils.Disposable;
import io.kestra.core.scheduler.SchedulerEventQueue;
import io.kestra.core.scheduler.events.SchedulerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple in-memory event queue implementation.
 * <p>
 * This class is for testing-purpose only.
 */
public class InMemorySchedulerEventQueue implements SchedulerEventQueue {
    private final List<Consumer<SchedulerEvent>> subscribers = new ArrayList<>();
    private final List<SchedulerEvent> sentEvents = new ArrayList<>();
    
    /** {@inheritDoc} **/
    @Override
    public void send(SchedulerEvent event) {
        sentEvents.add(event);
        for (Consumer<SchedulerEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }
    
    /** {@inheritDoc} **/
    @Override
    public Disposable subscribe(Consumer<SchedulerEvent> consumer) {
        subscribers.add(consumer);
        return Disposable.of(() -> subscribers.remove(consumer));
    }
    
    public List<SchedulerEvent> sentEvents() {
        return sentEvents;
    }
    
    public List<Consumer<SchedulerEvent>> subscribers() {
        return subscribers;
    }
    
    /** {@inheritDoc} **/
    @Override
    public void close() {
        subscribers.clear();
    }
}
