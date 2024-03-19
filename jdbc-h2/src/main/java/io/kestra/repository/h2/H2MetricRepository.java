package io.kestra.repository.h2;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2MetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public H2MetricRepository(@Named("metrics") H2Repository<MetricEntry> repository) {
        super(repository);
    }
}

