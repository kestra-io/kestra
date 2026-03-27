package io.kestra.runner.mysql;

import org.jooq.Condition;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcCleanerService;

import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlJdbcCleanerService implements JdbcCleanerService {
    @Override
    public Condition buildTypeCondition(String type) {
        return AbstractJdbcRepository.field("type").eq(type);
    }
}
