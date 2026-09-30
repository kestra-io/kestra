package io.kestra.core.server;

/**
 * The kind of operation {@link io.kestra.core.services.NotificationService#notifyAsyncOperation} is notified about.
 */
public enum AsyncOperationType {
    EXECUTION_KILL,
    EXECUTION_PAUSE,
    EXECUTION_RESUME,
    EXECUTION_RESTART,
    EXECUTION_REPLAY,
    EXECUTION_FORCE_RUN,
    EXECUTION_UNQUEUE,
    EXECUTION_CHANGE_STATUS,
    EXECUTION_SET_LABELS,
    TRIGGER_UNLOCK,
    TRIGGER_DELETE,
    TRIGGER_DISABLE,
    TRIGGER_ENABLE,
    BACKFILL_PAUSE,
    BACKFILL_RESUME,
    BACKFILL_DELETE,
}
