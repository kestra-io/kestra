package io.kestra.jdbc.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.repositories.IndexedFieldRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

public class AbstractJdbcIndexedFieldRepository extends io.kestra.jdbc.repository.AbstractJdbcRepository implements IndexedFieldRepositoryInterface {
    public static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    public static final Field<String> TENANT_ID_FIELD = field("tenant_id", String.class);
    public static final Field<String> FIELD_KEY_FIELD = field("field_key", String.class);
    public static final Field<String> FIELD_VALUE_FIELD = field("field_value", String.class);

    private final AbstractJdbcRepository<ExecutionIndexedField> jdbcRepository;

    public AbstractJdbcIndexedFieldRepository(AbstractJdbcRepository<ExecutionIndexedField> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public ExecutionIndexedField save(ExecutionIndexedField indexedField) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(7);
        fields.put(field("tenant_id"), indexedField.tenantId());
        fields.put(field("execution_id"), indexedField.executionId());
        fields.put(field("field_key"), indexedField.key());
        fields.put(field("field_value"), indexedField.value());
        fields.put(field("namespace"), indexedField.namespace());
        fields.put(field("flow_id"), indexedField.flowId());
        jdbcRepository.persist(indexedField, fields);
        return indexedField;
    }

    @Override
    public List<ExecutionIndexedField> findByExecution(Execution execution) {
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
    public List<String> findExecutionIds(String tenantId, String key, String value, boolean exactMatch) {
        var condition = FIELD_KEY_FIELD.eq(key)
            .and(exactMatch ? FIELD_VALUE_FIELD.eq(value) : FIELD_VALUE_FIELD.contains(value));

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .selectDistinct(EXECUTION_ID_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(condition);

                return select.fetch().map(record -> record.get(EXECUTION_ID_FIELD));
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

    private static ExecutionIndexedField map(org.jooq.Record record) {
        return new ExecutionIndexedField(
            record.get(TENANT_ID_FIELD),
            record.get(EXECUTION_ID_FIELD),
            record.get(FIELD_KEY_FIELD),
            record.get(FIELD_VALUE_FIELD),
            record.get(field("namespace", String.class)),
            record.get(field("flow_id", String.class))
        );
    }
}
