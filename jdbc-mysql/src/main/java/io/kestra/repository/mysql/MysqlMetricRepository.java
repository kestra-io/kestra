package io.kestra.repository.mysql;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.util.Date;

@Singleton
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
        switch (groupType) {
            case MONTH:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("DATE_FORMAT({0}, '%x-%v')", Date.class, DSL.field(dateField));
            case DAY:
                return DSL.field("DATE({0})", Date.class, DSL.field(dateField));
            case HOUR:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m-%d %H:00:00')", Date.class, DSL.field(dateField));
            case MINUTE:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m-%d %H:%i:00')", Date.class, DSL.field(dateField));
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}

