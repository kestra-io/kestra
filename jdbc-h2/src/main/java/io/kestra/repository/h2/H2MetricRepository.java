package io.kestra.repository.h2;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueService;
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
                              QueueService queueService,
                              JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}

