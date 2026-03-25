package io.kestra.core.runners;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.kestra.core.models.executions.LogEntry;

/**
 * Interface for emitting log entries to the log queue and the follow log queue.
 * <p>
 * This is the preferred way to send logs to the queue, sending them directly is not recommended.
 */
public interface LogEntryEmitter {

    /**
     * Emit a log entry to the log queue and the follow log queue.
     * <p>
     * This method is async, the log entry is not guaranteed to be emitted immediately, but it will be emitted eventually.
     */
    CompletionStage<Void> emits(LogEntry entry);

    /**
     * Emit a list of log entries to the log queue and the follow log queue.
     * <p>
     * This method is async, the log entry is not guaranteed to be emitted immediately, but it will be emitted eventually.
     */
    CompletionStage<Void> emits(List<LogEntry> entries);
}
