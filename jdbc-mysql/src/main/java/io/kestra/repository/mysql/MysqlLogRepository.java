package io.kestra.repository.mysql;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.jdbc.repository.AbstractJdbcLogRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Arrays;

@Singleton
@MysqlRepositoryEnabled
public class MysqlLogRepository extends AbstractJdbcLogRepository {
    @Inject
    public MysqlLogRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(LogEntry.class, applicationContext));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(
            Arrays.asList("namespace", "flow_id", "task_id", "execution_id", "taskrun_id", "trigger_id", "message", "thread"),
            query
        );
    }
}

