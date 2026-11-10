package io.kestra.core.async;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.async.AsyncOperationProcessedEvent.Progress;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.BroadcastQueueInterface;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest
class AsyncEventStreamingServiceTest {
    @Inject
    BroadcastQueueInterface<AsyncOperationProcessedEvent> queue;

    @Inject
    AsyncEventStreamingService service;

    @Test
    void shouldDispatchEventToSinkWhenSubscriberRegistered() throws Exception {
        // Given
        String opId = "op-1";
        String itemId = "exec-1";

        CompletableFuture<AsyncOperationProcessedEvent> future = new CompletableFuture<>();
        Flux.<AsyncOperationProcessedEvent> create(sink -> service.registerSubscriber(opId, itemId, sink))
            .timeout(Duration.ofSeconds(5))
            .doFinally(sig -> service.unregisterSubscriber(opId, itemId))
            .subscribe(future::complete, future::completeExceptionally);

        // The event crosses the queue as JSON, whose format carries microseconds; stamp it at the precision the wire can hold.
        AsyncOperationProcessedEvent event = new AsyncOperationProcessedEvent(
            opId, "tenant", itemId, Outcome.SUCCEEDED, null, Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        // When
        queue.emit(event);

        // Then
        AsyncOperationProcessedEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(event);
    }

    @Test
    void shouldDispatchEveryItemEventToOperationSubscriber() throws Exception {
        String opId = "op-follow";
        String subscriberId = "subscriber-1";
        CompletableFuture<List<AsyncOperationProcessedEvent>> future = new CompletableFuture<>();
        Flux.<AsyncOperationProcessedEvent> create(sink -> service.registerOperationSubscriber(opId, subscriberId, sink))
            .take(3)
            .timeout(Duration.ofSeconds(5))
            .doFinally(sig -> service.unregisterOperationSubscriber(opId, subscriberId))
            .collectList()
            .subscribe(future::complete, future::completeExceptionally);

        queue.emit(new AsyncOperationProcessedEvent(opId, null, "item-1", Outcome.SUCCEEDED, null, Instant.now()));
        queue.emit(new AsyncOperationProcessedEvent("another-op", null, "item-1", Outcome.SUCCEEDED, null, Instant.now()));
        queue.emit(new AsyncOperationProcessedEvent(opId, null, "item-2", Outcome.RUNNING, null, new Progress(1, -1), Instant.now()));
        queue.emit(new AsyncOperationProcessedEvent(opId, null, "item-2", Outcome.FAILED, "boom", Instant.now()));

        assertThat(future.get(5, TimeUnit.SECONDS))
            .extracting(AsyncOperationProcessedEvent::itemId, AsyncOperationProcessedEvent::outcome)
            .containsExactly(tuple("item-1", Outcome.SUCCEEDED), tuple("item-2", Outcome.RUNNING), tuple("item-2", Outcome.FAILED));
    }

    @Test
    void shouldNotBlowUpWhenNoSubscriberForEmittedEvent() throws Exception {
        // Given
        AsyncOperationProcessedEvent event = new AsyncOperationProcessedEvent(
            "unknown-op", "tenant", "item-x", Outcome.SUCCEEDED, null, Instant.now()
        );

        // When / Then: no subscribers registered — must not throw.
        queue.emit(event);
    }
}
