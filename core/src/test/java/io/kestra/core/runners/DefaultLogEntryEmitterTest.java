package io.kestra.core.runners;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.utils.TestsUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLogEntryEmitterTest {
    @Test
    void shouldDropUnsupportedSingleLogEntry() throws Exception {
        DispatchQueueInterface<LogEntry> logQueue = mock(DispatchQueueInterface.class);
        BroadcastQueueInterface<FollowLogEvent> followLogQueue = mock(BroadcastQueueInterface.class);
        when(followLogQueue.emitAsync(any(FollowLogEvent.class))).thenReturn(CompletableFuture.completedFuture(null));
        UnsupportedMessageException exception = new UnsupportedMessageException("bad unicode", new RuntimeException("surrogate"));
        when(logQueue.emitAsync(any(LogEntry.class))).thenReturn(failedStage(exception));

        DefaultLogEntryEmitter emitter = new DefaultLogEntryEmitter(logQueue, followLogQueue);
        emitter.emits(logEntry()).toCompletableFuture().join();

        verify(logQueue).emitAsync(any(LogEntry.class));
        verify(followLogQueue, never()).emitAsync(any(FollowLogEvent.class));
    }

    @Test
    void shouldDropUnsupportedLogEntryBatch() throws Exception {
        DispatchQueueInterface<LogEntry> logQueue = mock(DispatchQueueInterface.class);
        BroadcastQueueInterface<FollowLogEvent> followLogQueue = mock(BroadcastQueueInterface.class);
        when(followLogQueue.emitAsync(any(List.class))).thenReturn(CompletableFuture.completedFuture(null));
        UnsupportedMessageException exception = new UnsupportedMessageException("bad unicode", new RuntimeException("surrogate"));
        when(logQueue.emitAsync(any(List.class))).thenReturn(failedStage(exception));

        DefaultLogEntryEmitter emitter = new DefaultLogEntryEmitter(logQueue, followLogQueue);
        emitter.emits(List.of(logEntry())).toCompletableFuture().join();

        verify(logQueue).emitAsync(any(List.class));
        verify(followLogQueue, never()).emitAsync(any(List.class));
    }

    private CompletionStage<Void> failedStage(Throwable throwable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private LogEntry logEntry() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        return LogEntry.of(execution).toBuilder()
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread(Thread.currentThread().getName())
            .message("test\uD800text")
            .build();
    }
}
