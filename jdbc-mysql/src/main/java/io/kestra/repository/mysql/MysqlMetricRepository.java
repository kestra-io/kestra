package io.kestra.repository.mysql;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Field;

import java.sql.Timestamp;

@Singleton
@MysqlRepositoryEnabled
public class MysqlMetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public MysqlMetricRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(MetricEntry.class, applicationContext));
    }

    @Override
    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return this.jdbcRepository.weekFromTimestamp(timestampField);
    }
}

