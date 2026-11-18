package io.kestra.core.async;

import java.time.Instant;

import io.kestra.core.queues.event.BroadcastEvent;

import jakarta.annotation.Nullable;

/**
 * Broadcast event emitted by a domain consumer after processing a domain event
 * that implements {@link AsyncOperation}.
 * <p>
 * Carries the outcome and — when failed — a short error message. A {@link Outcome#RUNNING} event
 * reports the progress of an item still being processed and may be followed by others; only a
 * terminal outcome ends the item.
 * The resource state after processing is the authoritative source of truth;
 * controllers read it back after observing this event.
 */
public record AsyncOperationProcessedEvent(
    String operationId,
    @Nullable String tenantId,
    String itemId,
    Outcome outcome,
    @Nullable String error,
    @Nullable Progress progress,
    Instant timestamp) implements BroadcastEvent {

    public AsyncOperationProcessedEvent(String operationId, @Nullable String tenantId, String itemId, Outcome outcome, @Nullable String error, Instant timestamp) {
        this(operationId, tenantId, itemId, outcome, error, null, timestamp);
    }

    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED;

        public boolean isTerminal() {
            return RUNNING != this;
        }
    }

    /**
     * How far an item has got, in units chosen by its producer (bytes for a download).
     *
     * @param total the expected number of units, or a negative value when it is unknown.
     */
    public record Progress(long completed, long total) {
    }

    @Override
    public String key() {
        return operationId;
    }
}
