package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractJdbcExecutionQueuedStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository;

    public AbstractJdbcExecutionQueuedStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void save(ExecutionQueued executionQueued) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionQueued);
        this.jdbcRepository.persist(executionQueued, fields);
    }

    public void pop(String tenantId, String namespace, String flowId, Consumer<Execution> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId))
                    .orderBy(field("date").asc())
                    .limit(1)
                    .forUpdate();

                Optional<ExecutionQueued> maybeExecution = this.jdbcRepository.fetchOne(select);
                if (maybeExecution.isPresent()) {
                    consumer.accept(maybeExecution.get().getExecution());
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
}
