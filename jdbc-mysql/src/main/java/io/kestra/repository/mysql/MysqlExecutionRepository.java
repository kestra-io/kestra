package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public MysqlExecutionRepository(ApplicationContext applicationContext, AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(new MysqlRepository<>(Execution.class, applicationContext), applicationContext, executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "flow_id", "id"), query);
    }

    @Override
    protected Condition labelsFilter(Map<String, String> labels) {
        return DSL.and(labels.entrySet()
            .stream()
            .map(pair -> {
                    final Field<String> field = DSL.field("JSON_VALUE(value, '$.labels." + pair.getKey() + "' NULL ON EMPTY)", String.class);

                    if (pair.getValue() == null) {
                        return field.isNotNull();
                    } else {
                        return field.eq(pair.getValue());
                    }
                }
            ).collect(Collectors.toList()));
    }
}
