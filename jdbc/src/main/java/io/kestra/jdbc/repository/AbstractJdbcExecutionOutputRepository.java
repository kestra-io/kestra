package io.kestra.jdbc.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

public class AbstractJdbcExecutionOutputRepository extends io.kestra.jdbc.repository.AbstractJdbcRepository implements ExecutionOutputRepositoryInterface {
    public static final Field<byte[]> VALUE_FIELD = field("value", byte[].class);
    public static final Field<String> URI_ID_FIELD = field("uri", String.class);

    private final AbstractJdbcRepository<ExecutionOutput> jdbcRepository;

    public AbstractJdbcExecutionOutputRepository(AbstractJdbcRepository<ExecutionOutput> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public ExecutionOutput save(ExecutionOutput executionOutput) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(3);
        fields.put(field("tenant_id"), executionOutput.tenantId());
        fields.put(io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD, executionOutput.value());
        fields.put(field("uri"), executionOutput.uri());
        jdbcRepository.persist(executionOutput, fields);
        return executionOutput;
    }

    @Override
    public Optional<ExecutionOutput> findById(String tenantId, String executionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(KEY_FIELD.eq(executionId))
                    .limit(1);

                return Optional.ofNullable(select.fetchAny()).map(record -> map(record));
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
                    .where(KEY_FIELD.in(executionIds));

                return delete.execute();
            });
    }

    private static ExecutionOutput map(org.jooq.Record record) {
        return new ExecutionOutput(record.get(KEY_FIELD), record.get(TENANT_ID_FIELD), record.get(VALUE_FIELD), record.get(URI_ID_FIELD));
    }
}
