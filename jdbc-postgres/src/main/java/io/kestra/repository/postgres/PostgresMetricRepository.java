package io.kestra.repository.postgres;

import java.sql.Timestamp;
import java.util.Date;

import org.jooq.Field;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.kestra.jdbc.services.JdbcFilterService;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresMetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public PostgresMetricRepository(@Named("metrics") PostgresRepository<MetricEntry> repository,
        JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Field<Timestamp> groupByTimestampField(String dateField) {
        return PostgresRepositoryUtils.utcTimestampField(dateField);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }
}
