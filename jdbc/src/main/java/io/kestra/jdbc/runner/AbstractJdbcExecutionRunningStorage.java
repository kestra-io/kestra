package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Map;
import java.util.Optional;
import java.util.function.*;

public class AbstractJdbcExecutionRunningStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionRunning> jdbcRepository;

    public AbstractJdbcExecutionRunningStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutionRunning> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void save(ExecutionRunning executionRunning) {
        jdbcRepository.getDslContextWrapper().transaction(
            configuration -> save(DSL.using(configuration), executionRunning)
        );
    }

    public void save(DSLContext dslContext, ExecutionRunning executionRunning) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionRunning);
        this.jdbcRepository.persist(executionRunning, dslContext, fields);
    }

    /**
     * Count for running executions then process the count using the consumer function.
     * It locked the raw and is wrapped in a transaction so the consumer should use the provided dslContext for any database access.
     * <p>
     * Note: when there is no execution running, there will be no database locks, so multiple calls will return 0.
     * This is only potentially an issue with multiple executor instances when the concurrency limit is set to 1.
     */
    public ExecutionRunning countThenProcess(FlowInterface flow, BiFunction<DSLContext, Integer, ExecutionRunning> consumer) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var dslContext = DSL.using(configuration);
                var select = dslContext
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(flow.getTenantId()))
                    .and(field("namespace").eq(flow.getNamespace()))
                    .and(field("flow_id").eq(flow.getId()));

                Integer count = select.forUpdate().fetch().size();
                return consumer.apply(dslContext, count);
            });
    }

    /**
     * Delete the execution running corresponding to the given execution.
     */
    public void remove(Execution execution) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(execution.getTenantId()))
                    .and(field("key").eq(IdUtils.fromPartsAndSeparator('|', execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId())))
                    .forUpdate();

                Optional<ExecutionRunning> maybeExecution = this.jdbcRepository.fetchOne(select);
                maybeExecution.ifPresent(executionRunning -> this.jdbcRepository.delete(executionRunning));
            });
    }
}
