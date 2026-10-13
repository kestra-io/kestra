package io.kestra.core.notification.model;

/**
 * The processing state of a single {@link NotificationItem}.
 */
public enum NotificationItemOutcome {
    PENDING,
    SUCCEEDED,
    FAILED
}
