package io.kestra.repository.h2;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcLogRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;
import java.util.List;

@Singleton
@H2RepositoryEnabled
public class H2LogRepository extends AbstractJdbcLogRepository {
    @Inject
    public H2LogRepository(@Named("logs") H2Repository<LogEntry> repository,
                           QueueService queueService,
                           JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}

