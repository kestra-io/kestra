package io.kestra.scheduler.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import io.kestra.core.vnodes.VNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssignmentEvent;

/**
 * Simple in-memory vNodes assignment channel of the schedulers.
 * <p>
 * This class is for testing-purpose only.
 */
public class InMemoryVNodesAssignmentChannel implements VNodesAssignmentChannel {
    private final List<Consumer<VNodesAssignmentEvent>> subscribers = new ArrayList<>();
    private final List<VNodesAssignmentEvent> sentEvents = new ArrayList<>();

    @Override
    public ServiceType serviceType() {
        return ServiceType.SCHEDULER;
    }

    @Override
    public String controllerLockId() {
        return "controller";
    }

    @Override
    public void send(VNodesAssignmentEvent event) {
        sentEvents.add(event);
        for (Consumer<VNodesAssignmentEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    @Override
    public Disposable subscribe(Consumer<VNodesAssignmentEvent> consumer) {
        subscribers.add(consumer);
        return Disposable.of(() -> subscribers.remove(consumer));
    }

    public List<VNodesAssignmentEvent> sentEvents() {
        return sentEvents;
    }

    public List<Consumer<VNodesAssignmentEvent>> subscribers() {
        return subscribers;
    }
}
