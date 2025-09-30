package io.kestra.scheduler.events;


import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to reset a trigger.
 */
public record ResetTrigger(
    TriggerId id,
    Instant timestamp
) implements TriggerEvent {

    public ResetTrigger(TriggerId id) {
        this(id, Instant.now());
    }
}
