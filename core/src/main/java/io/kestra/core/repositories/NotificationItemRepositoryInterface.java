package io.kestra.core.repositories;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.notifications.NotificationItem;
import io.kestra.core.models.notifications.NotificationItemOutcome;

import jakarta.annotation.Nullable;

/**
 * One row per resource targeted by an async operation (see {@link NotificationItem}). Read by
 * {@link io.kestra.core.services.NotificationService} to project the succeeded/failed/total
 * counters onto the {@link io.kestra.core.models.notifications.Notification} it tracks.
 * <p>
 * {@code tenantId}, {@code operationId} and {@code resourceId} are the only indexed columns, via
 * the {@code (tenantId, operationId)} index; every query filters through it — a lookup by
 * {@code outcome} alone reads the full row(s) rather than filtering in SQL.
 */
public interface NotificationItemRepositoryInterface {

    /**
     * Bulk-inserts the {@code PENDING} rows for a freshly submitted operation, or upserts a single
     * row (e.g. to flip its outcome) when the caller already has every field it needs.
     */
    List<NotificationItem> create(List<NotificationItem> items);

    /**
     * Tallies the items of the operation tracked by {@code (tenantId, operationId)} by outcome,
     * via the {@code (tenantId, operationId)} index. Omits an outcome with a zero count.
     */
    Map<NotificationItemOutcome, Long> countByOperationId(@Nullable String tenantId, String operationId);

    /**
     * Resource ids of the {@code (tenantId, operationId)} items, optionally narrowed to one
     * {@code outcome}, via the {@code (tenantId, operationId)} index. Used by Elasticsearch/OpenSearch
     * execution/trigger search to resolve {@code operationId}/{@code operationOutcome} filters, since
     * that backend has no cross-index join/EXISTS equivalent to the JDBC correlated subquery.
     */
    List<String> findResourceIds(@Nullable String tenantId, String operationId, @Nullable NotificationItemOutcome outcome);

    /**
     * Retention purge, cascaded from {@code NotificationService#purge()}: deletes every item row
     * belonging to any of the given (already-purged) operations, via the
     * {@code (tenantId, operationId)} index.
     *
     * @return the number of deleted rows.
     */
    int deleteByOperationIds(List<TenantOperationId> operationIds);

    /**
     * A notification's {@code tenantId} and {@code operationId} (its {@code referenceId}) —
     * together, the {@code (tenantId, operationId)} index key for its {@link NotificationItem}s.
     */
    record TenantOperationId(@Nullable String tenantId, String operationId) {
    }
}
