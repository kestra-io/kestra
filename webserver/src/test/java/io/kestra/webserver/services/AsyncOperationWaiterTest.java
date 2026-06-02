package io.kestra.webserver.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class AsyncOperationWaiterTest {
    @Inject
    BroadcastQueueInterface<AsyncOperationProcessedEvent> queue;

    @Inject
    AsyncOperationWaiter waiter;

    @Test
    void shouldReturnProcessedEventWhenOutcomeSucceeded() throws java.util.concurrent.TimeoutException {
        AsyncOperationProcessedEvent received = waiter.submitAndWait(
            "item-1",
            operationId -> CompletableFuture.runAsync(() -> {
                try {
                    queue.emit(new AsyncOperationProcessedEvent(
                        operationId, "t", "item-1", Outcome.SUCCEEDED, null, Instant.now()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }),
            Duration.ofSeconds(5));

        assertThat(received.outcome()).isEqualTo(Outcome.SUCCEEDED);
    }

    @Test
    void shouldThrowTimeoutExceptionWhenNoEventArrivesWithinTimeout() {
        assertThatThrownBy(() ->
            waiter.submitAndWait("item-2", opId -> { /* emit nothing */ }, Duration.ofMillis(200))
        ).isInstanceOf(java.util.concurrent.TimeoutException.class);
    }
}
