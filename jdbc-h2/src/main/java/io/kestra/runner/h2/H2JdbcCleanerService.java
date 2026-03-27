package io.kestra.runner.h2;

import org.jooq.Condition;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcCleanerService;

import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2JdbcCleanerService implements JdbcCleanerService {
    @Override
    public Condition buildTypeCondition(String type) {
        return AbstractJdbcRepository.field("type").eq(type);
    }
}
