package io.kestra.core.models.notifications;

/**
 * The processing state of a single {@link NotificationItem}.
 */
public enum NotificationItemOutcome {
    PENDING,
    SUCCEEDED,
    FAILED
}
