package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.TransactionContext;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

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
    public void pop(String tenantId, String namespace, String flowId, BiConsumer<TransactionContext, Execution> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                var dslContext = DSL.using(configuration);
                var select = dslContext
                    .select(AbstractJdbcRepository.field("value"))
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
                    var txContext = new JdbcTransactionContext(dslContext);
                    consumer.accept(txContext, maybeExecution.get().getExecution());
                    this.jdbcRepository.delete(maybeExecution.get());
                }
            });
    }

    /**
     * This method should only be used for administration purpose via a command
     */
    public List<ExecutionQueued> getAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public void remove(Execution execution) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSL
                .using(configuration)
                .deleteFrom(this.jdbcRepository.getTable())
                .where(buildTenantCondition(execution.getTenantId()))
                .and(field("key").eq(IdUtils.fromParts(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId())))
                .execute();
            });
    }
}
