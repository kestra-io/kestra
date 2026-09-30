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
 * caller via {@code AccessibleTenantsProvider}) rather than by a single {@code tenantId} as for
 * most other repositories.
 */
public interface NotificationRepositoryInterface {

    Notification create(Notification notification);

    Optional<Notification> findById(String userId, String id);

    /**
     * Correlation lookup used by producers to upsert progress on an existing notification.
     */
    Optional<Notification> findByUserTypeAndReferenceId(String userId, String type, String referenceId);

    /**
     * Correlation lookup used by producers to upsert progress on an existing notification.
     */
    Optional<Notification> findByOperationId(String operationId);

    /**
     * History, cursor-based: rows created strictly before the cursor, most recent first.
     *
     * @param cursor {@code null} for the first page.
     */
    List<Notification> findByUser(String userId, Set<String> accessibleTenantIds, @Nullable NotificationCursor cursor, int limit);

    /**
     * Polling delta: rows updated strictly after {@code since}, oldest first.
     */
    List<Notification> findByUserSince(String userId, Set<String> accessibleTenantIds, Instant since);

    long countUnread(String userId, Set<String> accessibleTenantIds);

    Notification update(Notification notification);

    boolean markRead(String userId, String id);

    boolean markUnread(String userId, String id);

    /**
     * @return the notifications that were marked as read, so callers can emit an event per row.
     */
    List<Notification> markAllRead(String userId, Set<String> accessibleTenantIds);

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
