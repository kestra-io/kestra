package io.kestra.jdbc.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.CheckpointRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.AbstractJdbcRepository;

public class AbstractJdbcCheckpointRepository extends io.kestra.jdbc.repository.AbstractJdbcRepository implements CheckpointRepositoryInterface {
    public static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    public static final Field<String> TASK_RUN_ID_FIELD = field("task_run_id", String.class);
    public static final Field<String> CHECKPOINT_KEY_FIELD = field("checkpoint_key", String.class);
    public static final Field<String> CHECKPOINT_VALUE_FIELD = field("value", String.class);

    private final AbstractJdbcRepository<Checkpoint> jdbcRepository;

    public AbstractJdbcCheckpointRepository(AbstractJdbcRepository<Checkpoint> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Checkpoint save(Checkpoint checkpoint) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(5);
        fields.put(field("tenant_id"), checkpoint.tenantId());
        fields.put(field("execution_id"), checkpoint.executionId());
        fields.put(field("task_run_id"), checkpoint.taskRunId());
        fields.put(field("checkpoint_key"), checkpoint.key());
        fields.put(io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD, checkpoint.value());
        jdbcRepository.persist(checkpoint, fields);
        return checkpoint;
    }

    @Override
    public Optional<Checkpoint> findById(String tenantId, String executionId, String taskRunId, String key) {
        var condition = KEY_FIELD.eq(IdUtils.fromParts(tenantId, executionId, taskRunId, key));
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
    public List<Checkpoint> findByTaskRun(String tenantId, String executionId, String taskRunId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(EXECUTION_ID_FIELD.eq(executionId))
                    .and(TASK_RUN_ID_FIELD.eq(taskRunId))
                    .fetch()
                    .map(record -> map(record))
            );
    }

    @Override
    public int purge(List<Execution> executions) {
        List<String> executionIds = executions.stream().map(Execution::getId).toList();
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .delete(this.jdbcRepository.getTable())
                    .where(EXECUTION_ID_FIELD.in(executionIds))
                    .execute()
            );
    }

    private static Checkpoint map(org.jooq.Record record) {
        return new Checkpoint(
            record.get(TENANT_ID_FIELD),
            record.get(EXECUTION_ID_FIELD),
            record.get(TASK_RUN_ID_FIELD),
            record.get(CHECKPOINT_KEY_FIELD),
            record.get(CHECKPOINT_VALUE_FIELD)
        );
    }
}
