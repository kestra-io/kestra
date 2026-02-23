package io.kestra.worker;

import com.google.common.base.Suppliers;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.runners.DefaultLogEntryEmitter;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Implementation of {@link LogEntryEmitter} that emits log entries to intra worker queue {@link WorkerQueue}.
 */
@Singleton
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultLogEntryEmitter.class)
public class WorkerLogEntryEmitter implements LogEntryEmitter {
    
    private final Supplier<WorkerQueue<LogEntry>> workerLogQueue;

    /**
     * Creates a new {@link WorkerLogEntryEmitter} instance;
     *
     * @param workerQueueRegistry The worker queue registry used to retrieve the worker log queue.
     */
    public WorkerLogEntryEmitter(WorkerQueueRegistry workerQueueRegistry) {
        // Lazily retrieve the worker log queue from the registry.
        // Queue is created during the worker initialization, so we need to delay the retrieval until it's available.
        this.workerLogQueue = Suppliers.memoize(() -> workerQueueRegistry.get(LogEntry.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Void> emits(LogEntry entry) {
        this.workerLogQueue.get().put(entry);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Void> emits(List<LogEntry> entries) {
        entries.forEach(this::emits);
        return CompletableFuture.completedFuture(null);
    }
}
