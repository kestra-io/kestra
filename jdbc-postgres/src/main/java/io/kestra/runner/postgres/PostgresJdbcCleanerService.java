package io.kestra.runner.postgres;

import io.kestra.jdbc.runner.JdbcCleanerService;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

@Singleton
@PostgresQueueEnabled
public class PostgresJdbcCleanerService implements JdbcCleanerService {
    @Override
    public Condition buildTypeCondition(String type) {
        return DSL.condition("type = CAST(? AS queue_type)", type);
    }
}
