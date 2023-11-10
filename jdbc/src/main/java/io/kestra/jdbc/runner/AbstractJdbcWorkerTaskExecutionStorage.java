package io.kestra.jdbc.runner;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJdbcWorkerTaskExecutionStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerTaskExecution<?>> jdbcRepository;

    public AbstractJdbcWorkerTaskExecutionStorage(io.kestra.jdbc.AbstractJdbcRepository jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public Optional<WorkerTaskExecution<?>> get(String executionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        AbstractJdbcRepository.field("key").eq(executionId)
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public void save(List<WorkerTaskExecution<?>> workerTaskExecutions) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                // TODO batch insert
                workerTaskExecutions.forEach(workerTaskExecution -> {
                    Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(workerTaskExecution);
                    this.jdbcRepository.persist(workerTaskExecution, context, fields);
                });
            });
    }

    public void delete(WorkerTaskExecution<?> workerTaskExecution) {
        this.jdbcRepository.delete(workerTaskExecution);
    }
}
