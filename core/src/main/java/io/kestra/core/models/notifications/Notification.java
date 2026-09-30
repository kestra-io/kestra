package io.kestra.core.models.notifications;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.server.AsyncOperationType;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single in-app notification for a user.
 * <p>
 * Notifications are user-global: they are not scoped to a single tenant the way most tenant-aware
 * entities are. {@code tenantId} is kept for deep-linking and for re-checking, at read time, that
 * the user still has access to the tenant the notification originated from.
 * <p>
 * {@code referenceId} is the correlation key producers use to update an existing notification
 * (e.g. an async operation's progress) instead of creating a new one for every event.
 * <p>
 * {@code type} is the {@link NotificationType#key()} of the producer's type rather than the
 * interface itself: a producer outside this module (e.g. an EE feature) contributes its own
 * {@link NotificationType} implementation, so the wire/storage representation has to be the plain
 * key rather than a polymorphic value this module cannot deserialize.
 */
@Builder(toBuilder = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Notification implements HasUID {
    private String id;

    @Nullable
    private String userId;

    @Nullable
    private String tenantId;

    @NotNull
    @Builder.Default
    private String type = CoreNotificationType.GENERIC.name();

    /**
     * The {@link AsyncOperationType#name()} of the specific operation this notification reports on,
     * for {@link CoreNotificationType#ASYNC_OPERATION}; {@code null} for every other type. Stored as
     * the plain name rather than the interface itself, for the same reason {@link #type} is — see
     * that field's Javadoc.
     */
    @Nullable
    private String asyncOperationType;

    /**
     * The kind of resource {@link #asyncOperationType} targets, captured at creation time since this
     * module cannot otherwise recover it from the stored name alone. {@code null} for every type but
     * {@link CoreNotificationType#ASYNC_OPERATION}.
     */
    @Nullable
    private AsyncOperationType.ResourceType resourceType;

    @NotNull
    private String title;

    /**
     * Correlation key for updatable notifications (future case ID, async operation ID, ...).
     * {@code null} for one-shot notifications that are never updated.
     */
    @Nullable
    private String referenceId;

    /**
     * Progress indicators for {@link CoreNotificationType#ASYNC_OPERATION}, {@code null} for every
     * other type. These are <strong>not</strong> persisted: the source of truth is the
     * {@code notification_items} table (see {@link NotificationItem}), one row per targeted
     * resource. {@link io.kestra.core.services.NotificationService} populates these fields only on
     * the copy it returns to a caller or emits as a {@link NotificationEvent}, by aggregating that
     * table — never on a {@code Notification} passed into {@code create}/{@code update}.
     */
    @Nullable
    private Integer succeededItems;

    @Nullable
    private Integer failedItems;

    @Nullable
    private Integer totalItems;

    @Builder.Default
    private boolean read = false;

    @NotNull
    private Instant createdDate;

    @NotNull
    private Instant updatedDate;

    @Override
    public String uid() {
        return id;
    }

    /**
     * The terminal outcome of a progress-tracked notification, or {@code null} while it is still
     * ongoing (see {@link #isOngoing()}) or when it does not track progress at all.
     */
    @Nullable
    public NotificationOutcome getOutcome() {
        if (totalItems == null || Boolean.TRUE.equals(isOngoing())) {
            return null;
        }
        int succeeded = succeededItems != null ? succeededItems : 0;
        int failed = failedItems != null ? failedItems : 0;
        if (failed == 0) {
            return NotificationOutcome.SUCCEEDED;
        }
        if (succeeded == 0) {
            return NotificationOutcome.FAILED;
        }
        return NotificationOutcome.PARTIAL;
    }

    public Boolean isOngoing() {
        if (totalItems == null) {
            return null;
        }
        int succeeded = succeededItems != null ? succeededItems : 0;
        int failed = failedItems != null ? failedItems : 0;
        return totalItems != succeeded + failed;
    }
}
