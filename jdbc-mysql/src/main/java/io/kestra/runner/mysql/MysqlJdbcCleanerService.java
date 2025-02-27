package io.kestra.runner.mysql;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcCleanerService;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@MysqlQueueEnabled
public class MysqlJdbcCleanerService implements JdbcCleanerService {
    @Override
    public Condition buildTypeCondition(String type) {
        return AbstractJdbcRepository.field("type").eq(type);
    }
}
