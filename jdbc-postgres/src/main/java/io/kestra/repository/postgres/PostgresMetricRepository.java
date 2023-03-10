package io.kestra.repository.postgres;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresMetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public PostgresMetricRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(MetricEntry.class, applicationContext));
    }
}

