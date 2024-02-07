package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import jakarta.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Singleton
public abstract class AbstractJdbcLogRepository extends AbstractJdbcRepository implements LogRepositoryInterface, JdbcIndexerInterface<LogEntry> {
    protected io.kestra.jdbc.AbstractJdbcRepository<LogEntry> jdbcRepository;

    public AbstractJdbcLogRepository(io.kestra.jdbc.AbstractJdbcRepository<LogEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                if (namespace != null) {
                    select.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
                }

                if (flowId != null) {
                    select.and(field("flow_id").eq(flowId));
                }

                if (minLevel != null) {
                    select = select.and(minLevel(minLevel));
                }

                if (query != null) {
                    select = select.and(this.findCondition(query));
                }

                if (startDate != null) {
                    select = select.and(field("timestamp").greaterOrEqual(startDate.toOffsetDateTime()));
                }

                if (endDate != null) {
                    select = select.and(field("timestamp").lessOrEqual(endDate.toOffsetDateTime()));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId),
            minLevel
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId),
            minLevel,
            pageable
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            minLevel
        );
    }
    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            minLevel,
            pageable
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            minLevel
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            minLevel,
            pageable
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
                .and(field("attempt_number").eq(attempt)),
            minLevel
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
                .and(field("attempt_number").eq(attempt)),
            minLevel,
            pageable
        );
    }

    @Override
    public LogEntry save(LogEntry log) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(log);
        this.jdbcRepository.persist(log, fields);

        return log;
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
    public LogEntry save(DSLContext dslContext, LogEntry logEntry) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(logEntry);
        this.jdbcRepository.persist(logEntry, dslContext, fields);

        return logEntry;
    }

    @Override
    public void delete(LogEntry logEntry) {
        this.jdbcRepository.delete(logEntry);
    }

    private ArrayListTotal<LogEntry> query(String tenantId, Condition condition, Level minLevel, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                if (minLevel != null) {
                    select.and(minLevel(minLevel));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable
                );
            });
    }

    private List<LogEntry> query(String tenantId, Condition condition, Level minLevel) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                if (minLevel != null) {
                    select.and(minLevel(minLevel));
                }

                return this.jdbcRepository.fetch(select
                    .orderBy(field("timestamp").sort(SortOrder.ASC))
                );
            });
    }

    protected Condition minLevel(Level minLevel) {
        return field("level").in(LogEntry.findLevelsByMin(minLevel));
    }
}
