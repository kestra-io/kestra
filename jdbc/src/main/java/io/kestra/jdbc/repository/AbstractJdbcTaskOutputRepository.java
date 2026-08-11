package io.kestra.jdbc.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

public class AbstractJdbcTaskOutputRepository extends io.kestra.jdbc.repository.AbstractJdbcRepository implements TaskOutputRepositoryInterface {
    public static final Field<String> TASK_RUN_ID_FIELD = field("task_run_id", String.class);
    public static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    public static final Field<byte[]> VALUE_FIELD = field("value", byte[].class);
    public static final Field<String> URI_ID_FIELD = field("uri", String.class);

    private final AbstractJdbcRepository<TaskOutput> jdbcRepository;

    public AbstractJdbcTaskOutputRepository(AbstractJdbcRepository<TaskOutput> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public TaskOutput save(TaskOutput taskOutput) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(5);
        fields.put(field("task_run_id"), taskOutput.taskRunId());
        fields.put(field("tenant_id"), taskOutput.tenantId());
        fields.put(field("execution_id"), taskOutput.executionId());
        fields.put(io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD, taskOutput.value());
        fields.put(field("uri"), taskOutput.uri());
        jdbcRepository.persist(taskOutput, fields);
        return taskOutput;
    }

    @Override
    public Optional<TaskOutput> findById(String tenantId, String taskRunId) {
        var condition = TASK_RUN_ID_FIELD.eq(taskRunId);
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(condition)
                    .limit(1);

                return Optional.ofNullable(select.fetchAny()).map(record -> map(record));
            });
    }

    @Override
    public List<TaskOutput> findByExecution(Execution execution) {
        var condition = EXECUTION_ID_FIELD.eq(execution.getId());
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(execution.getTenantId()))
                    .and(condition);

                return select.fetch().map(record -> map(record));
            });
    }

    @Override
    public int purgeByExecutionIds(List<String> executionIds) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var delete = DSL
                    .using(configuration)
                    .delete(this.jdbcRepository.getTable())
                    .where(EXECUTION_ID_FIELD.in(executionIds));

                return delete.execute();
            });
    }

    private static TaskOutput map(org.jooq.Record record) {
        return new TaskOutput(record.get(TASK_RUN_ID_FIELD), record.get(TENANT_ID_FIELD), record.get(EXECUTION_ID_FIELD), record.get(VALUE_FIELD), record.get(URI_ID_FIELD));
    }
}
