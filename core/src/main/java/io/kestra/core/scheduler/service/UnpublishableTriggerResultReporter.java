package io.kestra.core.scheduler.service;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.event.Level;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.core.utils.Logs;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Reports a trigger result that the queue refused because of its content, so that polling and realtime
 * triggers fail the same visible way: an error in the trigger logs, and no execution.
 */
@Singleton
public class UnpublishableTriggerResultReporter {
    private final LogEntryEmitter logEntryEmitter;

    @Inject
    public UnpublishableTriggerResultReporter(LogEntryEmitter logEntryEmitter) {
        this.logEntryEmitter = Objects.requireNonNull(logEntryEmitter);
    }

    /**
     * @return {@code true} when the queue refused the message because of its size or its content, so that
     *         publishing it again would fail the same way.
     */
    public static boolean isUnpublishable(Throwable e) {
        return e instanceof MessageTooBigException || e instanceof UnsupportedMessageException;
    }

    public void report(TriggerId triggerId, Throwable cause) {
        Logs.logTrigger(triggerId, Level.ERROR, "Unable to publish trigger result: {}", cause.getMessage(), cause);
        logEntryEmitter.emits(LogEntry.builder()
            .tenantId(triggerId.getTenantId())
            .namespace(triggerId.getNamespace())
            .flowId(triggerId.getFlowId())
            .triggerId(triggerId.getTriggerId())
            .executionId(triggerId.getTriggerId())
            .level(Level.ERROR)
            .message(("Unable to publish the result of this trigger, so no execution was created: %s "
                + "Reduce the amount of data the trigger returns in a single evaluation.").formatted(cause.getMessage()))
            .timestamp(Instant.now())
            .thread(Thread.currentThread().getName())
            .build()
        );
    }
}
