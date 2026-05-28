package io.kestra.core.async;

import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        Flux.<AsyncOperationProcessedEvent>create(sink -> service.registerSubscriber(opId, itemId, sink))
            .timeout(Duration.ofSeconds(5))
            .doFinally(sig -> service.unregisterSubscriber(opId, itemId))
            .subscribe(future::complete, future::completeExceptionally);

        AsyncOperationProcessedEvent event = new AsyncOperationProcessedEvent(
            opId, "tenant", itemId, Outcome.SUCCEEDED, null, Instant.now());

        // When
        queue.emit(event);

        // Then
        AsyncOperationProcessedEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(event);
    }

    @Test
    void shouldNotBlowUpWhenNoSubscriberForEmittedEvent() throws Exception {
        // Given
        AsyncOperationProcessedEvent event = new AsyncOperationProcessedEvent(
            "unknown-op", "tenant", "item-x", Outcome.SUCCEEDED, null, Instant.now());

        // When / Then: no subscribers registered — must not throw.
        queue.emit(event);
    }
}
