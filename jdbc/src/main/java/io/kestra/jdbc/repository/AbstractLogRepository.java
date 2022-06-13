package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@Singleton
public abstract class AbstractLogRepository extends AbstractRepository implements LogRepositoryInterface, JdbcIndexerInterface<LogEntry> {
    protected AbstractJdbcRepository<LogEntry> jdbcRepository;

    public AbstractLogRepository(AbstractJdbcRepository<LogEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
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
                    .where(this.defaultFilter());

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
    public List<LogEntry> findByExecutionId(String id, Level minLevel) {
        return this.query(
            field("execution_id").eq(id),
            minLevel
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            minLevel
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            minLevel
        );
    }

    @Override
    public LogEntry save(LogEntry log) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(log);
        this.jdbcRepository.persist(log, fields);

        return log;
    }

    @Override
    public LogEntry save(DSLContext dslContext, LogEntry logEntry) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(logEntry);
        this.jdbcRepository.persist(logEntry, dslContext, fields);

        return logEntry;
    }

    private List<LogEntry> query(Condition condition, Level minLevel) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

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
