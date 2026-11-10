import type {Notification, NotificationOutcome} from "@kestra-io/kestra-sdk"

const OUTCOME_COLORS: Record<NotificationOutcome | "RUNNING", string> = {
    RUNNING: "var(--ks-status-running)",
    SUCCEEDED: "var(--ks-status-success)",
    PARTIAL: "var(--ks-status-warning)",
    FAILED: "var(--ks-status-failed)",
}

export function progressColor(notification: Notification) {
    return OUTCOME_COLORS[notification.outcome ?? "RUNNING"]
}

/** Null when the operation has no known total, so callers show no bar rather than an invented one. */
export function progressPercentage(notification: Notification) {
    const total = notification.totalItems
    if (!total) return null

    const processed = (notification.succeededItems ?? 0) + (notification.failedItems ?? 0)
    return Math.min(100, Math.round((processed / total) * 100))
}
