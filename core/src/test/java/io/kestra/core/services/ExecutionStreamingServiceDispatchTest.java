package io.kestra.core.services;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.utils.Either;

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
 * Tests the delivery guarantees of the {@link ExecutionStreamingService} queue consumer: it must never
 * propagate an exception, as the shared queue subscriber treats any escaping exception as fatal and
 * shuts down the server (see kestra-io/kestra#17730).
 */
class ExecutionStreamingServiceDispatchTest {
    private static final String EXECUTION_ID = "execution-id";

    private QueueSubscriber<FollowExecutionEvent> queueSubscriber;
    private ExecutionRepositoryInterface executionRepository;
    private ExecutionStreamingService service;
    private Consumer<Either<FollowExecutionEvent, DeserializationException>> dispatcher;

    private final Flow flow = Flow.builder().id("flow").namespace("io.kestra.tests").build();
    private final Execution execution = Execution.builder()
        .id(EXECUTION_ID)
        .namespace("io.kestra.tests")
        .flowId("flow")
        .state(new State())
        .build();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void init() {
        this.queueSubscriber = mock(QueueSubscriber.class);
        this.executionRepository = mock(ExecutionRepositoryInterface.class);
        BroadcastQueueInterface<FollowExecutionEvent> queue = mock(BroadcastQueueInterface.class);
        when(queue.subscriber()).thenReturn(queueSubscriber);
        when(executionRepository.findByIdWithoutAcl(null, EXECUTION_ID)).thenReturn(Optional.of(execution));

        this.service = new ExecutionStreamingService(queue, mock(ExecutionService.class), executionRepository);
        this.service.startQueueConsumer();

        ArgumentCaptor<Consumer<Either<FollowExecutionEvent, DeserializationException>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(queueSubscriber).subscribe(captor.capture());
        this.dispatcher = captor.getValue();
    }

    @Test
    void shouldKeepDeliveringToOtherSubscribersWhenOneSinkThrows() {
        // Given a stale subscriber whose sink throws on delivery, and a healthy one
        FluxSink<Event<Execution>> stale = sink();
        doThrow(new IllegalStateException("response writer already closed")).when(stale).next(any());
        FluxSink<Event<Execution>> healthy = sink();

        service.registerSubscriber(EXECUTION_ID, "stale", stale, flow);
        service.registerSubscriber(EXECUTION_ID, "healthy", healthy, flow);

        // When an event is dispatched
        // Then the failure is swallowed, so the shared queue consumer is not stopped
        assertThatCode(() -> dispatcher.accept(Either.left(event()))).doesNotThrowAnyException();
        verify(healthy).next(any());
        verify(stale).error(any(IllegalStateException.class));
    }

    @Test
    void shouldUnregisterSubscriberWhenSinkIsAlreadyCancelled() {
        // Given a subscriber whose SSE stream is already closed
        FluxSink<Event<Execution>> cancelled = sink();
        when(cancelled.isCancelled()).thenReturn(true);
        service.registerSubscriber(EXECUTION_ID, "cancelled", cancelled, flow);

        // When an event is dispatched
        dispatcher.accept(Either.left(event()));

        // Then nothing is written to it and it is unregistered, which pauses the queue as nobody listens anymore
        verify(cancelled, never()).next(any());
        verify(queueSubscriber, times(2)).pause(); // once on startup, once when the last subscriber is gone
    }

    @Test
    void shouldNotFailWhenExecutionIsNotFound() {
        FluxSink<Event<Execution>> sink = sink();
        service.registerSubscriber(EXECUTION_ID, "subscriber", sink, flow);
        when(executionRepository.findByIdWithoutAcl(null, EXECUTION_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> dispatcher.accept(Either.left(event()))).doesNotThrowAnyException();
        verify(sink, never()).next(any());
    }

    @Test
    void shouldNotFailWhenEventCannotBeDeserialized() {
        assertThatCode(() -> dispatcher.accept(Either.right(new DeserializationException("boom"))))
            .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private FluxSink<Event<Execution>> sink() {
        return mock(FluxSink.class);
    }

    private FollowExecutionEvent event() {
        return new FollowExecutionEvent(execution, ExecutionEventType.UPDATED);
    }
}
