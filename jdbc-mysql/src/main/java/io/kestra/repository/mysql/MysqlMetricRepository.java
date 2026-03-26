package io.kestra.repository.mysql;

import java.sql.Timestamp;
import java.util.Date;

import org.jooq.Field;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.kestra.jdbc.services.JdbcFilterService;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlMetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public MysqlMetricRepository(@Named("metrics") MysqlRepository<MetricEntry> repository,
        JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return this.jdbcRepository.weekFromTimestamp(timestampField);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }
}
