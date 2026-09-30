package io.kestra.jdbc.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.NotificationRepositoryInterface;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Notifications are user-global, not tenant-scoped: the inherited {@code defaultFilter(tenantId)}
 * / {@code defaultFilter()} are neutralized here (see class Javadoc on
 * {@link NotificationRepositoryInterface}) and every query instead builds its own condition on
 * {@code user_id}, plus {@link #accessibleTenantsCondition} for cross-tenant visibility.
 */
public class AbstractJdbcNotificationRepository extends AbstractJdbcCrudRepository<Notification> implements NotificationRepositoryInterface {

    public AbstractJdbcNotificationRepository(io.kestra.jdbc.AbstractJdbcRepository<Notification> jdbcRepository) {
        super(jdbcRepository);
    }

    /**
     * Persists the notification on a dedicated connection, committed before this method returns,
     * so it is immediately visible to other processes even when the caller holds an open
     * thread-bound transaction (e.g. the dispatch-queue poll transaction).
     */
    @Override
    public Notification create(Notification notification) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(notification);
        this.jdbcRepository.getDslContextWrapper().requireNewTransaction(
            configuration -> this.jdbcRepository.persist(notification, DSL.using(configuration), fields)
        );
        return notification;
    }

    @Override
    public Optional<Notification> findById(String userId, String id) {
        return findOne(DSL.noCondition(), field("user_id").eq(userId).and(field("id").eq(id)));
    }

    @Override
    public Optional<Notification> findByUserTypeAndReferenceId(String userId, String type, String referenceId) {
        return findOne(
            DSL.noCondition(),
            field("user_id").eq(userId)
                .and(field("type").eq(type))
                .and(field("reference_id").eq(referenceId))
        );
    }

    @Override
    public Optional<Notification> findByOperationId(String operationId) {
        return findOne(DSL.noCondition(), field("reference_id").eq(operationId));
    }

    @Override
    public List<Notification> findByUser(String userId, Set<String> accessibleTenantIds, @Nullable NotificationCursor cursor, int limit) {
        Condition condition = field("user_id").eq(userId).and(accessibleTenantsCondition(accessibleTenantIds));

        if (cursor != null) {
            OffsetDateTime cursorDate = toOffsetDateTime(cursor.createdDate());
            condition = condition.and(
                field("created_date").lessThan(cursorDate)
                    .or(field("created_date").eq(cursorDate).and(field("id").lessThan(cursor.id())))
            );
        }

        return findPage(
            Pageable.from(1, limit),
            DSL.noCondition(),
            condition,
            field("created_date").desc(),
            field("id").desc()
        );
    }

    @Override
    public List<Notification> findByUserSince(String userId, Set<String> accessibleTenantIds, Instant since) {
        Condition condition = field("user_id").eq(userId)
            .and(accessibleTenantsCondition(accessibleTenantIds))
            .and(field("updated_date").greaterThan(toOffsetDateTime(since)));

        return find(DSL.noCondition(), condition, field("updated_date").asc());
    }

    @Override
    public long countUnread(String userId, Set<String> accessibleTenantIds) {
        Condition condition = field("user_id").eq(userId)
            .and(field("read").isFalse())
            .and(accessibleTenantsCondition(accessibleTenantIds));

        return this.jdbcRepository.count(condition);
    }

    @Override
    public Notification update(Notification notification) {
        this.findById(notification.getUserId(), notification.getId())
            .ifPresent(existing -> this.jdbcRepository.persist(notification));
        return notification;
    }

    @Override
    public boolean markRead(String userId, String id) {
        return findById(userId, id)
            .map(notification ->
            {
                update(notification.toBuilder().read(true).updatedDate(Instant.now()).build());
                return true;
            })
            .orElse(false);
    }

    @Override
    public boolean markUnread(String userId, String id) {
        return findById(userId, id)
            .map(notification ->
            {
                update(notification.toBuilder().read(false).updatedDate(Instant.now()).build());
                return true;
            })
            .orElse(false);
    }

    @Override
    public List<Notification> markAllRead(String userId, Set<String> accessibleTenantIds) {
        Condition condition = field("user_id").eq(userId)
            .and(field("read").isFalse())
            .and(accessibleTenantsCondition(accessibleTenantIds));

        List<Notification> unread = find(DSL.noCondition(), condition);
        if (unread.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<Notification> updated = unread.stream().map(notification -> notification.toBuilder().read(true).updatedDate(now).build()).toList();
        saveBatch(updated);
        return updated;
    }

    @Override
    public int deleteByQuery(Instant readOlderThan, Instant createdOlderThan) {
        Condition condition = field("read").isTrue().and(field("updated_date").lessThan(toOffsetDateTime(readOlderThan)))
            .or(field("created_date").lessThan(toOffsetDateTime(createdOlderThan)));

        return purge(DSL.noCondition(), condition);
    }

    /**
     * Rows with a {@code null} tenant (global, not tied to a tenant) are always visible; rows tied
     * to a tenant are only visible while the user still has access to that tenant.
     */
    private Condition accessibleTenantsCondition(Set<String> accessibleTenantIds) {
        Condition condition = field("tenant_id").isNull();
        if (accessibleTenantIds != null && !accessibleTenantIds.isEmpty()) {
            condition = condition.or(field("tenant_id").in(accessibleTenantIds));
        }
        return condition;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(ZoneId.of("UTC")).toOffsetDateTime();
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return DSL.noCondition();
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.noCondition();
    }
}
