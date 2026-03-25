package io.kestra.core.runners;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "kestra.queue.type")
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
        this.logQueue.emitAsync(entry);
        return this.followLogQueue.emitAsync(FollowLogEvent.from(entry));
    }

    @Override
    public CompletionStage<Void> emits(List<LogEntry> entries) {
        this.logQueue.emitAsync(entries);
        return this.followLogQueue.emitAsync(entries.stream().map(FollowLogEvent::from).toList());
    }
}
