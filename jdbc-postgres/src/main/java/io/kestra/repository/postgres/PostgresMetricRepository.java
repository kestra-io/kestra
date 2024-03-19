package io.kestra.repository.postgres;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresMetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public PostgresMetricRepository(@Named("metrics") PostgresRepository<MetricEntry> repository) {
        super(repository);
    }
}

