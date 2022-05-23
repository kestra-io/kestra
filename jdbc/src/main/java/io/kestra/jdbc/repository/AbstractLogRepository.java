package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
public abstract class AbstractLogRepository extends AbstractRepository implements LogRepositoryInterface {
    protected AbstractJdbcRepository<LogEntry> jdbcRepository;

    public AbstractLogRepository(AbstractJdbcRepository<LogEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<LogEntry> find(String query, Pageable pageable, Level minLevel) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(DSL.field("value"))
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                if (minLevel != null) {
                    select.and(minLevel(minLevel));
                }

                if (query != null) {
                    select.and(this.findCondition(query));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public List<LogEntry> findByExecutionId(String id, Level minLevel) {
        return this.query(
            DSL.field("execution_id").eq(id),
            minLevel
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel) {
        return this.query(
            DSL.field("execution_id").eq(executionId)
                .and(DSL.field("task_id").eq(taskId)),
            minLevel
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel) {
        return this.query(
            DSL.field("execution_id").eq(executionId)
                .and(DSL.field("taskrun_id").eq(taskRunId)),
            minLevel
        );
    }

    @Override
    public LogEntry save(LogEntry log) {
        Map<Field<Object>, Object> finalFields = this.jdbcRepository.persistFields(log);

        this.jdbcRepository
            .getDslContext()
            .transaction(configuration -> DSL
                .using(configuration)
                .insertInto(this.jdbcRepository.getTable())
                .set(finalFields)
                .execute()
            );

        return log;
    }

    private List<LogEntry> query(Condition condition, Level minLevel) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                select = select.and(condition);

                if (minLevel != null) {
                    select.and(minLevel(minLevel));
                }

                return this.jdbcRepository.fetch(select
                    .orderBy(DSL.field("timestamp").sort(SortOrder.ASC))
                );
            });
    }

    private static Condition minLevel(Level minLevel) {
        return DSL.field("level").in(LogEntry.findLevelsByMin(minLevel));
    }
}
