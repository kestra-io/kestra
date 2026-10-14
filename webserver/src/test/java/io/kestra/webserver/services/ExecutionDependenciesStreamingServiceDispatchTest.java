package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Either;
import io.kestra.webserver.controllers.api.ExecutionStatusEvent;

import io.micronaut.http.sse.Event;
import reactor.core.publisher.FluxSink;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the delivery guarantees of the {@link ExecutionDependenciesStreamingService} queue consumer: it must
 * never propagate an exception, as the shared queue subscriber treats any escaping exception as fatal and
 * shuts down the server (see kestra-io/kestra#17730).
 */
class ExecutionDependenciesStreamingServiceDispatchTest {
    private static final String TENANT_ID = "main";
    private static final String NAMESPACE = "io.kestra.tests";
    private static final String FLOW_ID = "flow";
    private static final String EXECUTION_ID = "execution-id";
    private static final String CORRELATION_ID = "correlation-id";

    private QueueSubscriber<FollowExecutionEvent> queueSubscriber;
    private ExecutionRepositoryInterface executionRepository;
    private ExecutionDependenciesStreamingService service;
    private Consumer<Either<FollowExecutionEvent, DeserializationException>> dispatcher;

    private final Execution execution = Execution.builder()
        .id(EXECUTION_ID)
        .tenantId(TENANT_ID)
        .namespace(NAMESPACE)
        .flowId(FLOW_ID)
        .state(new State())
        .labels(List.of(new Label(Label.CORRELATION_ID, CORRELATION_ID)))
        .build();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void init() {
        this.queueSubscriber = mock(QueueSubscriber.class);
        this.executionRepository = mock(ExecutionRepositoryInterface.class);
        BroadcastQueueInterface<FollowExecutionEvent> queue = mock(BroadcastQueueInterface.class);
        when(queue.subscriber()).thenReturn(queueSubscriber);
        when(executionRepository.findByIdWithoutAcl(TENANT_ID, EXECUTION_ID)).thenReturn(Optional.of(execution));

        this.service = new ExecutionDependenciesStreamingService(queue, mock(ExecutionService.class), executionRepository);
        this.service.startQueueConsumer();

        ArgumentCaptor<Consumer<Either<FollowExecutionEvent, DeserializationException>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(queueSubscriber).subscribe(captor.capture());
        this.dispatcher = captor.getValue();
    }

    @Test
    void shouldNotFailWhenExecutionIsNotFound() {
        // Given a subscriber and an event whose execution cannot be found anymore
        FluxSink<Event<ExecutionStatusEvent>> sink = sink();
        service.registerSubscriber(CORRELATION_ID, "subscriber", subscriber(sink));
        when(executionRepository.findByIdWithoutAcl(TENANT_ID, EXECUTION_ID)).thenReturn(Optional.empty());

        // When an event is dispatched
        // Then the event is skipped instead of failing the shared queue consumer
        assertThatCode(() -> dispatcher.accept(Either.left(event()))).doesNotThrowAnyException();
        verify(sink, never()).next(any());
    }

    @Test
    void shouldKeepDeliveringToOtherSubscribersWhenOneSinkThrows() {
        // Given a stale subscriber whose sink throws on delivery, and a healthy one
        FluxSink<Event<ExecutionStatusEvent>> stale = sink();
        doThrow(new IllegalStateException("response writer already closed")).when(stale).next(any());
        FluxSink<Event<ExecutionStatusEvent>> healthy = sink();

        service.registerSubscriber(CORRELATION_ID, "stale", subscriber(stale));
        service.registerSubscriber(CORRELATION_ID, "healthy", subscriber(healthy));

        // When an event is dispatched
        // Then the failure is swallowed, so the shared queue consumer is not stopped
        assertThatCode(() -> dispatcher.accept(Either.left(event()))).doesNotThrowAnyException();
        verify(healthy).next(any());
        verify(stale).error(any(IllegalStateException.class));
    }

    @Test
    void shouldUnregisterSubscriberWhenSinkIsAlreadyCancelled() {
        // Given a subscriber whose SSE stream is already closed
        FluxSink<Event<ExecutionStatusEvent>> cancelled = sink();
        when(cancelled.isCancelled()).thenReturn(true);
        service.registerSubscriber(CORRELATION_ID, "cancelled", subscriber(cancelled));

        // When an event is dispatched
        dispatcher.accept(Either.left(event()));

        // Then nothing is written to it and it is unregistered, which pauses the queue as nobody listens anymore
        verify(cancelled, never()).next(any());
        verify(queueSubscriber, times(2)).pause(); // once on startup, once when the last subscriber is gone
    }

    @Test
    void shouldNotFailWhenEventCannotBeDeserialized() {
        assertThatCode(() -> dispatcher.accept(Either.right(new DeserializationException("boom"))))
            .doesNotThrowAnyException();
    }

    private ExecutionDependenciesStreamingService.Subscriber subscriber(FluxSink<Event<ExecutionStatusEvent>> sink) {
        FlowNode node = FlowNode.builder()
            .uid(FlowId.uidWithoutRevision(TENANT_ID, NAMESPACE, FLOW_ID))
            .tenantId(TENANT_ID)
            .namespace(NAMESPACE)
            .id(FLOW_ID)
            .build();
        Flow flow = Flow.builder().tenantId(TENANT_ID).namespace(NAMESPACE).id(FLOW_ID).build();

        return new ExecutionDependenciesStreamingService.Subscriber(
            CORRELATION_ID,
            new ArrayList<>(List.of(node)), // must be modifiable: terminated dependencies are removed from it
            Map.of(FlowId.uidWithoutRevision(flow), flow),
            sink
        );
    }

    @SuppressWarnings("unchecked")
    private FluxSink<Event<ExecutionStatusEvent>> sink() {
        return mock(FluxSink.class);
    }

    private FollowExecutionEvent event() {
        return new FollowExecutionEvent(execution, ExecutionEventType.UPDATED);
    }
}
