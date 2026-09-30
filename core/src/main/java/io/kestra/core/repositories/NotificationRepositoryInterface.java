package io.kestra.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.models.notifications.Notification;

import jakarta.annotation.Nullable;

/**
 * Notifications are user-global: every query is scoped by {@code userId} and, for cross-tenant
 * visibility, by the set of tenants the requesting user currently has access to (resolved by the
 * calling controller) rather than by a single {@code tenantId} as for most other repositories.
 * {@code userId} is {@code null} for OSS's single implicit user (no real user model) — every
 * query treats it like any other nullable column rather than refusing it.
 */
public interface NotificationRepositoryInterface {

    Notification create(Notification notification);

    Optional<Notification> findById(@Nullable String userId, String id);

    /**
     * Unscoped lookup by id, used internally to resolve the owner of a
     * {@link io.kestra.core.models.notifications.NotificationEvent} before routing it to the right
     * subscriber's SSE stream — the caller doesn't know {@code userId} yet.
     */
    Optional<Notification> findById(String id);

    /**
     * Correlation lookup used by producers to upsert progress on an existing notification.
     */
    Optional<Notification> findByUserTypeAndReferenceId(@Nullable String userId, String type, String referenceId);

    /**
     * Correlation lookup used by producers to upsert progress on an existing notification.
     */
    Optional<Notification> findByOperationId(String operationId);

    /**
     * Resolves the notification a {@link io.kestra.core.models.notifications.NotificationEvent}
     * refers to: by {@code notificationId} if present, otherwise by {@code referenceId}.
     */
    default Optional<Notification> resolve(@Nullable String notificationId, @Nullable String referenceId) {
        return notificationId != null ? findById(notificationId) : findByOperationId(referenceId);
    }

    /**
     * History, cursor-based: rows created strictly before the cursor, most recent first.
     *
     * @param cursor {@code null} for the first page.
     */
    List<Notification> findByUser(@Nullable String userId, Set<String> accessibleTenantIds, @Nullable NotificationCursor cursor, int limit);

    /**
     * Polling delta: rows updated strictly after {@code since}, oldest first.
     */
    List<Notification> findByUserSince(@Nullable String userId, Set<String> accessibleTenantIds, Instant since);

    long countUnread(@Nullable String userId, Set<String> accessibleTenantIds);

    Notification update(Notification notification);

    boolean markRead(@Nullable String userId, String id);

    boolean markUnread(@Nullable String userId, String id);

    /**
     * @return the notifications that were marked as read, so callers can emit an event per row.
     */
    List<Notification> markAllRead(@Nullable String userId, Set<String> accessibleTenantIds);

    /**
     * Same selection {@link #deleteByQuery} would delete, without deleting — used to cascade the
     * purge to {@code notification_items} first (via each notification's {@code tenantId} and
     * {@code referenceId}).
     */
    List<Notification> findToPurge(Instant readOlderThan, Instant createdOlderThan);

    /**
     * Flat-TTL retention purge (see {@code NotificationService#purge()}).
     *
     * @param readOlderThan delete read notifications with {@code updatedDate} older than this instant.
     * @param createdOlderThan delete any notification with {@code createdDate} older than this instant.
     * @return the number of deleted rows.
     */
    int deleteByQuery(Instant readOlderThan, Instant createdOlderThan);

    record NotificationCursor(Instant createdDate, String id) {
    }
}
