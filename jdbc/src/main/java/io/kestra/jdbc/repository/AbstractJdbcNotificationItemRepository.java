package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.notifications.NotificationItem;
import io.kestra.core.models.notifications.NotificationItemOutcome;
import io.kestra.core.repositories.NotificationItemRepositoryInterface;

/**
 * {@code notification_items} is not tenant-scoped the way most tables are: like {@code notifications}
 * (see {@link AbstractJdbcNotificationRepository}), every query builds its own condition instead of
 * relying on the inherited {@code defaultFilter(tenantId)}/{@code defaultFilter()}, which are
 * neutralized here.
 * <p>
 * Every operation-scoped lookup (tallying, purge) filters through the {@code (tenant_id,
 * operation_id)} index rather than the physical {@code "key"} column.
 */
public class AbstractJdbcNotificationItemRepository extends AbstractJdbcCrudRepository<NotificationItem> implements NotificationItemRepositoryInterface {

    public AbstractJdbcNotificationItemRepository(io.kestra.jdbc.AbstractJdbcRepository<NotificationItem> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public List<NotificationItem> create(List<NotificationItem> items) {
        saveBatch(items);
        return items;
    }

    @Override
    public Map<NotificationItemOutcome, Long> countByOperationId(String tenantId, String operationId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .select(field("outcome", String.class), DSL.count())
                    .from(this.jdbcRepository.getTable())
                    .where(tenantAndOperationCondition(tenantId, operationId))
                    .groupBy(field("outcome"))
                    .fetchMap(r -> NotificationItemOutcome.valueOf(r.value1()), r -> r.value2().longValue())
            );
    }

    @Override
    public List<String> findResourceIds(String tenantId, String operationId, NotificationItemOutcome outcome) {
        Condition condition = tenantAndOperationCondition(tenantId, operationId);
        if (outcome != null) {
            condition = condition.and(field("outcome").eq(outcome.name()));
        }
        Condition finalCondition = condition;

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .select(field("resource_id", String.class))
                    .from(this.jdbcRepository.getTable())
                    .where(finalCondition)
                    .fetch(r -> r.value1())
            );
    }

    @Override
    public int deleteByOperationIds(List<TenantOperationId> operationIds) {
        if (operationIds.isEmpty()) {
            return 0;
        }

        Condition condition = operationIds.stream()
            .map(ref -> tenantAndOperationCondition(ref.tenantId(), ref.operationId()))
            .reduce(Condition::or)
            .orElseThrow();

        return purge(DSL.noCondition(), condition);
    }

    private Condition tenantAndOperationCondition(String tenantId, String operationId) {
        Condition tenantCondition = tenantId == null ? field("tenant_id").isNull() : field("tenant_id").eq(tenantId);
        return tenantCondition.and(field("operation_id").eq(operationId));
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
