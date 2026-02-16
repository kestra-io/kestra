package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public abstract class AbstractJdbcExecutionQueuedStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository;

    public AbstractJdbcExecutionQueuedStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void save(DSLContext dslContext, ExecutionQueued executionQueued) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionQueued);
        this.jdbcRepository.persist(executionQueued, dslContext, fields);
    }

    /**
     * Pop the next queued execution.
     * This method is intended to be part of a larger transaction,
     * see {@link AbstractJdbcConcurrencyLimitStorage#decrementAndPop(FlowInterface, AbstractJdbcExecutionQueuedStorage, BiConsumer)}
     */
    public void pop(DSLContext dslContext, String tenantId, String namespace, String flowId, BiConsumer<DSLContext, Execution> consumer) {
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
            consumer.accept(dslContext, maybeExecution.get().getExecution());
            this.jdbcRepository.delete(maybeExecution.get());
        }
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
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    public void remove(Execution execution) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSL
                .using(configuration)
                .deleteFrom(this.jdbcRepository.getTable())
                .where(buildTenantCondition(execution.getTenantId()))
                .and(KEY_FIELD.eq(IdUtils.fromParts(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId())))
                .execute();
            });
    }
}
