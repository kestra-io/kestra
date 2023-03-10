package io.kestra.repository.h2;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2MetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public H2MetricRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(MetricEntry.class, applicationContext));
    }
}

