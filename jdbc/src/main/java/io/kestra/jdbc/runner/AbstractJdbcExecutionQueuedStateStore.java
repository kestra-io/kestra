package io.kestra.jdbc.runner;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

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

    /**
     * Lock (FOR UPDATE SKIP LOCKED) and return the oldest queued execution within a scope —
     * a single flow when {@code flowId} is set, a namespace and its descendants when only
     * {@code namespace} is set, the whole tenant when both are null — excluding already-tried
     * entries. The row is only locked, not deleted: the caller pops it with
     * {@link #delete(TransactionContext, ExecutionQueued)} once it accepted the candidate.
     * Used by the multi-scope concurrency release to scan pop candidates.
     */
    public Optional<ExecutionQueued> lockNextCandidate(TransactionContext txContext, String tenantId, String namespace, String flowId, Collection<String> excludedUids) {
        var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();

        var condition = buildTenantCondition(tenantId);
        if (flowId != null) {
            condition = condition.and(field("namespace").eq(namespace)).and(field("flow_id").eq(flowId));
        } else if (namespace != null) {
            condition = condition.and(field("namespace", String.class).eq(namespace).or(field("namespace", String.class).startsWith(namespace + ".")));
        }
        if (!excludedUids.isEmpty()) {
            condition = condition.and(KEY_FIELD.notIn(excludedUids));
        }

        var select = dslContext
            .select(VALUE_FIELD)
            .from(this.jdbcRepository.getTable())
            .where(condition)
            .orderBy(field("date").asc())
            .limit(1)
            .forUpdate()
            .skipLocked();

        return this.jdbcRepository.fetchOne(select);
    }

    /**
     * Delete a queued execution within the caller's transaction.
     */
    public void delete(TransactionContext txContext, ExecutionQueued executionQueued) {
        this.jdbcRepository.delete(txContext.unwrap(JdbcTransactionContext.class).getDslContext(), executionQueued);
    }

    /**
     * This method should only be used for administration purpose via a command
     */
    public List<ExecutionQueued> getAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable());

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
