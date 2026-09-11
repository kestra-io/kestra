package io.kestra.core.scheduler.events;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * Supported event or command types for trigger.
 */
public enum TriggerEventType {
    // EVENTS
    TRIGGER_CREATED,
    TRIGGER_UPDATED,
    TRIGGER_FLOW_REVISION_UPDATED,
    TRIGGER_DELETED,
    TRIGGER_EVALUATED,
    TRIGGER_EXECUTION_TERMINATED,
    TRIGGER_RECEIVED,
    TRIGGER_WORKER_LOST,
    // COMMANDS,
    CREATE_BACKFILL_TRIGGER,
    DELETE_BACKFILL_TRIGGER,
    SET_PAUSE_BACKFILL_TRIGGER,
    RESET_TRIGGER,
    SET_DISABLE_TRIGGER,
    // ERROR
    INVALID;

    @JsonCreator
    static TriggerEventType from(final String s) {
        return Enums.getForNameIgnoreCase(s, TriggerEventType.class, INVALID);
    }
}
