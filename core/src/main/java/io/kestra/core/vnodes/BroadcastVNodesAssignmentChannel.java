package io.kestra.core.vnodes;

import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

/**
 * The vNodes of one type of services, assigned through the {@link VNodesAssignmentEvent} queue that all the groups
 * share: only the messages of that type are delivered.
 */
public class BroadcastVNodesAssignmentChannel implements VNodesAssignmentChannel {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcastVNodesAssignmentChannel.class);

    private final BroadcastQueueInterface<VNodesAssignmentEvent> queue;
    private final ServiceType serviceType;
    private final String controllerLockId;

    public BroadcastVNodesAssignmentChannel(BroadcastQueueInterface<VNodesAssignmentEvent> queue, ServiceType serviceType, String controllerLockId) {
        this.queue = queue;
        this.serviceType = serviceType;
        this.controllerLockId = controllerLockId;
    }

    @Override
    public ServiceType serviceType() {
        return serviceType;
    }

    @Override
    public String controllerLockId() {
        return controllerLockId;
    }

    @Override
    public void send(VNodesAssignmentEvent event) {
        try {
            queue.emit(event);
        } catch (QueueException e) {
            throw new KestraRuntimeException("Unexpected error while publishing a vNodes assignment event", e);
        }
    }

    @Override
    public Disposable subscribe(Consumer<VNodesAssignmentEvent> consumer) {
        QueueSubscriber<VNodesAssignmentEvent> subscriber = queue.subscriber();
        subscriber.subscribe(either -> either.fold(
            Optional::ofNullable,
            e ->
            {
                LOG.warn("Failed to deserialize a vNodes assignment event. Cause: {}", e.getMessage());
                return Optional.<VNodesAssignmentEvent> empty();
            }
        ).filter(event -> serviceType == event.serviceType()).ifPresent(consumer));
        return Disposable.of(subscriber::close);
    }
}
