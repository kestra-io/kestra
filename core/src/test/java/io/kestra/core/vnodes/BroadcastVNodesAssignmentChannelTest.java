package io.kestra.core.vnodes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BroadcastVNodesAssignmentChannelTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldOnlyDeliverTheEventsOfItsServiceType() {
        BroadcastQueueInterface<VNodesAssignmentEvent> queue = mock(BroadcastQueueInterface.class);
        QueueSubscriber<VNodesAssignmentEvent> subscriber = mock(QueueSubscriber.class);
        AtomicReference<Consumer<Either<VNodesAssignmentEvent, DeserializationException>>> delivery = new AtomicReference<>();
        when(queue.subscriber()).thenReturn(subscriber);
        when(subscriber.subscribe(any())).thenAnswer(invocation ->
        {
            delivery.set(invocation.getArgument(0));
            return subscriber;
        });
        List<VNodesAssignmentEvent> received = new ArrayList<>();

        new BroadcastVNodesAssignmentChannel(queue, ServiceType.CONTROLLER, "worker-controller").subscribe(received::add);
        VNodesAssignmentEvent forControllers = new VNodesAssignmentEvent.VNodesAssignmentRequest(Instant.now(), ServiceType.CONTROLLER, "server-1", Instant.now(), Set.of("controller-1"));
        delivery.get().accept(Either.left(new VNodesAssignmentEvent.VNodesAssignmentRequest(Instant.now(), ServiceType.EXECUTOR, "server-1", Instant.now(), Set.of("executor-1"))));
        delivery.get().accept(Either.left(forControllers));

        assertThat(received).containsExactly(forControllers);
    }
}
