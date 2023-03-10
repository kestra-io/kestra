package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;

@Singleton
public abstract class AbstractJdbcMetricRepository extends AbstractJdbcRepository implements MetricRepositoryInterface, JdbcIndexerInterface<MetricEntry> {
    protected io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository;

    public AbstractJdbcMetricRepository(io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public List<MetricEntry> findByExecutionId(String id) {
        return this.query(
            field("execution_id").eq(id)
        );
    }

    @Override
    public List<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId))
        );
    }

    @Override
    public List<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
        );
    }

    @Override
    public MetricEntry save(MetricEntry metric) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(metric);
        this.jdbcRepository.persist(metric, fields);

        return metric;
    }

    @Override
    public Integer purge(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                return context.delete(this.jdbcRepository.getTable())
                    .where(field("execution_id", String.class).eq(execution.getId()))
                    .execute();
            });
    }

    @Override
    public MetricEntry save(DSLContext dslContext, MetricEntry metric) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(metric);
        this.jdbcRepository.persist(metric, dslContext, fields);

        return metric;
    }

    private List<MetricEntry> query(Condition condition) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                select = select.and(condition);

                return this.jdbcRepository.fetch(select
                    .orderBy(field("timestamp").sort(SortOrder.ASC))
                );
            });
    }
}
