package io.kestra.runner.h2;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcCleanerService;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@H2QueueEnabled
public class H2JdbcCleanerService implements JdbcCleanerService {
    @Override
    public Condition buildTypeCondition(String type) {
        return AbstractJdbcRepository.field("type").eq(type);
    }
}
