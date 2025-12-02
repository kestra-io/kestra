package io.kestra.core.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A trigger was received by a worker.
 */
public record TriggerReceived(
    TriggerId id,
    Instant timestamp,
    String workerId
) implements TriggerEvent{
}
