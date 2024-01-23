package io.kestra.jdbc.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJdbcSubflowExecutionStorage extends AbstractJdbcRepository {
    private final static ObjectMapper MAPPER = JdbcMapper.of();
    protected io.kestra.jdbc.AbstractJdbcRepository<SubflowExecution<?>> jdbcRepository;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public AbstractJdbcSubflowExecutionStorage(io.kestra.jdbc.AbstractJdbcRepository jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public Optional<SubflowExecution<?>> get(String executionId) {
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

                try {
                    return this.jdbcRepository.fetchOne(select);
                } catch (DeserializationException deserializationException) {
                    // we may fail to deserialize a SubflowExecution if we fail to deserialize its task
                    var jsonNode = MAPPER.readTree(deserializationException.getRecord());
                    var taskRun = MAPPER.treeToValue(jsonNode.get("parentTaskRun"), TaskRun.class);
                    var execution = MAPPER.treeToValue(jsonNode.get("execution"), Execution.class);
                    return Optional.of(SubflowExecution.builder()
                        .parentTaskRun(taskRun)
                        .execution(execution)
                        .build());
                }
            });
    }

    public void save(List<SubflowExecution<?>> subflowExecutions) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                // TODO batch insert
                subflowExecutions.forEach(subflowExecution -> {
                    Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(subflowExecution);
                    this.jdbcRepository.persist(subflowExecution, context, fields);
                });
            });
    }

    public void delete(SubflowExecution<?> subflowExecution) {
        this.jdbcRepository.delete(subflowExecution);
    }
}
