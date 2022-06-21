package io.kestra.runner.postgres;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.List;

@Singleton
@PostgresQueueEnabled
public class PostgresMultipleConditionStorage extends AbstractJdbcMultipleConditionStorage {
    public PostgresMultipleConditionStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(MultipleConditionWindow.class, applicationContext));
    }
}
