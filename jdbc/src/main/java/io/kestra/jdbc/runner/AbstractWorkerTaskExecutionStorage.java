package io.kestra.jdbc.runner;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractWorkerTaskExecutionStorage extends AbstractRepository {
    protected AbstractJdbcRepository<WorkerTaskExecution> jdbcRepository;

    public AbstractWorkerTaskExecutionStorage(AbstractJdbcRepository<WorkerTaskExecution> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public Optional<WorkerTaskExecution> get(String executionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        DSL.field(DSL.quotedName("key")).eq(executionId)
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public void save(List<WorkerTaskExecution> workerTaskExecutions) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                workerTaskExecutions.forEach(workerTaskExecution -> {
                    Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(workerTaskExecution);
                    this.jdbcRepository.persist(workerTaskExecution, context, fields);
                });
            });
    }

    public void delete(WorkerTaskExecution workerTaskExecution) {
        this.jdbcRepository.delete(workerTaskExecution);
    }
}
