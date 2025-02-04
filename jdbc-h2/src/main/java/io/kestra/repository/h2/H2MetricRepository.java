package io.kestra.repository.h2;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

@Singleton
@H2RepositoryEnabled
public class H2MetricRepository extends AbstractJdbcMetricRepository {
    @Inject
    public H2MetricRepository(@Named("metrics") H2Repository<MetricEntry> repository,
                              JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM')", Date.class);
            case WEEK:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'YYYY-ww')", Date.class);
            case DAY:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd')", Date.class);
            case HOUR:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd HH:00:00')", Date.class);
            case MINUTE:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd HH:mm:00')", Date.class);
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}

