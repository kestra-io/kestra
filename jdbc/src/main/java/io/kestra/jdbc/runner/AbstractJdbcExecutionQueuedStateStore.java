package io.kestra.jdbc.runner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

import io.micronaut.core.annotation.Nullable;

public abstract class AbstractJdbcExecutionQueuedStateStore extends AbstractJdbcRepository implements ExecutionQueuedStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository;

    public AbstractJdbcExecutionQueuedStateStore(io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void save(TransactionContext txContext, ExecutionQueued executionQueued) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionQueued);
        this.jdbcRepository.persist(executionQueued, txContext.unwrap(JdbcTransactionContext.class).getDslContext(), fields);
    }

    @Override
    public int count(TransactionContext txContext, String tenantId, String namespace, String flowId) {
        var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
        return dslContext
            .fetchCount(
                this.jdbcRepository.getTable(),
                buildTenantCondition(tenantId)
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId))
            );
    }

    @Override
    public int count(String tenantId, @Nullable String namespace, @Nullable String flowId) {
        var condition = buildTenantCondition(tenantId);
        if (flowId != null) {
            condition = condition.and(field("namespace").eq(namespace)).and(field("flow_id").eq(flowId));
        } else if (namespace != null) {
            condition = condition.and(field("namespace", String.class).eq(namespace).or(field("namespace", String.class).startsWith(namespace + ".")));
        }

        Condition scope = condition;
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL.using(configuration).fetchCount(this.jdbcRepository.getTable(), scope));
    }

    @Override
    public void pop(TransactionContext txContext, String tenantId, String namespace, String flowId, BiConsumer<TransactionContext, Execution> consumer) {
        var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
        var select = dslContext
            .select(VALUE_FIELD)
            .from(this.jdbcRepository.getTable())
            .where(buildTenantCondition(tenantId))
            .and(field("namespace").eq(namespace))
            .and(field("flow_id").eq(flowId))
            .orderBy(field("date").asc())
            .limit(1)
            .forUpdate()
            .skipLocked();

        Optional<ExecutionQueued> maybeExecution = this.jdbcRepository.fetchOne(select);
        if (maybeExecution.isPresent()) {
            consumer.accept(txContext, maybeExecution.get().getExecution());
            this.jdbcRepository.delete(maybeExecution.get());
        }
    }

    @Override
    public List<ExecutionQueued> getAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .orderBy(field("date").asc());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public void remove(Execution execution) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                DSL
                    .using(configuration)
                    .deleteFrom(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(execution.getTenantId()))
                    .and(KEY_FIELD.eq(IdUtils.fromParts(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId())))
                    .execute();
            });
    }
}
