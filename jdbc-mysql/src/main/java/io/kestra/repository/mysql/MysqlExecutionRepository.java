package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;

import java.sql.Timestamp;
import java.util.Map;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public MysqlExecutionRepository(ApplicationContext applicationContext, AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(new MysqlRepository<>(Execution.class, applicationContext), applicationContext, executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return MysqlExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return this.jdbcRepository.weekFromTimestamp(timestampField);
    }
}
