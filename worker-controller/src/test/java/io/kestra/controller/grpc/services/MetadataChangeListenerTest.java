package io.kestra.controller.grpc.services;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.Either;
import io.kestra.core.worker.MetadataChangePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetadataChangeListenerTest {

    private BroadcastQueueInterface<FlowInterface> flowQueue;
    private QueueSubscriber<FlowInterface> flowSubscriber;
    private MetadataChangeListener listener;
    @SuppressWarnings("unchecked")
    private final Consumer<MetadataChangePayload> consumer = mock(Consumer.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        flowQueue = mock(BroadcastQueueInterface.class);
        flowSubscriber = mock(QueueSubscriber.class);
        when(flowQueue.subscriber()).thenReturn(flowSubscriber);
        listener = new MetadataChangeListener(flowQueue);
    }

    @Test
    void shouldForwardFlowChangeAsFlowPayload() {
        // Given
        listener.start(consumer);
        Consumer<Either<FlowInterface, io.kestra.core.exceptions.DeserializationException>> callback = captureCallback();

        FlowInterface flow = mock(FlowInterface.class);
        when(flow.getTenantId()).thenReturn("tenant-a");
        when(flow.getNamespace()).thenReturn("prod.team");

        // When
        callback.accept(Either.left(flow));

        // Then
        ArgumentCaptor<MetadataChangePayload> payloadCaptor = ArgumentCaptor.forClass(MetadataChangePayload.class);
        verify(consumer).accept(payloadCaptor.capture());
        MetadataChangePayload payload = payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo(MetadataChangePayload.Type.FLOW);
        assertThat(payload.tenantId()).isEqualTo("tenant-a");
        assertThat(payload.namespace()).isEqualTo("prod.team");
    }

    @Test
    void shouldIgnoreDeserializationErrors() {
        // Given
        listener.start(consumer);
        Consumer<Either<FlowInterface, io.kestra.core.exceptions.DeserializationException>> callback = captureCallback();

        // When
        callback.accept(Either.right(new io.kestra.core.exceptions.DeserializationException("boom")));

        // Then
        verifyNoInteractions(consumer);
    }

    @Test
    void shouldCloseSubscriberOnStop() throws Exception {
        // Given
        listener.start(consumer);

        // When
        listener.stop();

        // Then
        verify(flowSubscriber).close();
    }

    @SuppressWarnings("unchecked")
    private Consumer<Either<FlowInterface, io.kestra.core.exceptions.DeserializationException>> captureCallback() {
        ArgumentCaptor<Consumer<Either<FlowInterface, io.kestra.core.exceptions.DeserializationException>>> cap =
            ArgumentCaptor.forClass(Consumer.class);
        verify(flowSubscriber).subscribe(cap.capture());
        return cap.getValue();
    }
}
