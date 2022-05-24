package io.kestra.runner.postgres;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresMultipleConditionStorage  extends AbstractJdbcMultipleConditionStorage {
    public PostgresMultipleConditionStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(MultipleConditionWindow.class, applicationContext));
    }
}
