package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.async.AsyncOperation;
import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A command to disable/enable a trigger.
 *
 * @param recoverMissedSchedules when {@code true}, missed schedules are recovered on re-enable according to the
 *                               trigger's own {@code recoverMissedSchedules} configuration;
 *                               {@code null} or {@code false} means missed schedules are skipped.
 */
public record SetDisableTrigger(
    TriggerId id,
    boolean disabled,
    @Nullable Boolean recoverMissedSchedules,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {

    public SetDisableTrigger(TriggerId id, Boolean disabled) {
        this(id, disabled, null);
    }

    public SetDisableTrigger(TriggerId id, Boolean disabled, @Nullable Boolean recoverMissedSchedules) {
        this(id, disabled, recoverMissedSchedules, Instant.now(), EventId.create(), null);
    }

    public SetDisableTrigger withOperationId(String operationId) {
        return new SetDisableTrigger(id, disabled, recoverMissedSchedules, timestamp, eventId, operationId);
    }
}
