package io.kestra.core.async;

import io.kestra.core.queues.event.BroadcastEvent;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * Broadcast event emitted by a domain consumer after processing a domain event
 * that implements {@link AsyncOperation}.
 * <p>
 * Carries the outcome (succeeded/failed) and — when failed — a short error message.
 * The resource state after processing is the authoritative source of truth;
 * controllers read it back after observing this event.
 */
public record AsyncOperationProcessedEvent(
    String operationId,
    String tenantId,
    String itemId,
    Outcome outcome,
    @Nullable String error,
    Instant timestamp
) implements BroadcastEvent {

    public enum Outcome { SUCCEEDED, FAILED }

    @Override
    public String key() {
        return operationId;
    }
}
