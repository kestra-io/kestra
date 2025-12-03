package io.kestra.core.scheduler.events;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A trigger was received by a worker.
 */
public record TriggerReceived(
    TriggerId id,
    String workerId,
    Instant timestamp,
    EventId eventId
) implements TriggerEvent{
    
    public TriggerReceived(TriggerId id, String workerId) {
        this(id, workerId, Instant.now(), EventId.create());
    }
}
