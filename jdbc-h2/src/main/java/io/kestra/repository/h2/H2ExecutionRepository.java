package io.kestra.repository.h2;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(ApplicationContext applicationContext, AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(new H2Repository<>(Execution.class, applicationContext), applicationContext, executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    @Override
    protected Condition labelsFilter(Map<String, String> labels) {
        return DSL.and(labels.entrySet()
            .stream()
            .map(pair -> {
                    final Field<String> field = DSL.field("JQ_STRING(\"value\", '.labels." + pair.getKey() + "')", String.class);

                    if (pair.getValue() == null) {
                        return field.isNotNull();
                    } else {
                        return field.eq(pair.getValue());
                    }
                }
            ).collect(Collectors.toList()));
    }
}
