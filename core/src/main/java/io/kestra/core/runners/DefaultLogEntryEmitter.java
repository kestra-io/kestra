package io.kestra.core.runners;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.UnsupportedMessageException;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Requires(property = "kestra.queue.type")
@Slf4j
public class DefaultLogEntryEmitter implements LogEntryEmitter {

    private final DispatchQueueInterface<LogEntry> logQueue;
    private final BroadcastQueueInterface<FollowLogEvent> followLogQueue;

    @Inject
    public DefaultLogEntryEmitter(DispatchQueueInterface<LogEntry> logQueue,
        BroadcastQueueInterface<FollowLogEvent> followLogQueue) {
        this.logQueue = logQueue;
        this.followLogQueue = followLogQueue;
    }

    @Override
    public CompletionStage<Void> emits(LogEntry entry) {
        return guardUnsupported(
            () -> this.logQueue.emitAsync(entry)
                .thenCompose(ignored -> this.followLogQueue.emitAsync(FollowLogEvent.from(entry))),
            () -> log.warn("Dropping unsupported log entry for execution {} due to invalid Unicode payload", entry.getExecutionId())
        );
    }

    @Override
    public CompletionStage<Void> emits(List<LogEntry> entries) {
        return guardUnsupported(
            () -> this.logQueue.emitAsync(entries)
                .thenCompose(ignored -> this.followLogQueue.emitAsync(entries.stream().map(FollowLogEvent::from).toList())),
            () -> {
                String executionId = entries.isEmpty() ? null : entries.getFirst().getExecutionId();
                log.warn("Dropping unsupported log entries for execution {} due to invalid Unicode payload", executionId);
            }
        );
    }

    private CompletionStage<Void> guardUnsupported(Supplier<CompletionStage<Void>> emitter, Runnable onUnsupported) {
        return emitter.get()
            .handle((ignored, throwable) -> {
                if (throwable == null) {
                    return null;
                }

                Throwable cause = unwrapCompletionException(throwable);
                if (cause instanceof UnsupportedMessageException) {
                    onUnsupported.run();
                    return null;
                }

                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new CompletionException(cause);
            });
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;

        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}
