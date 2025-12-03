package io.kestra.repository.postgres;

import io.kestra.core.models.dashboards.filters.AbstractFilter;
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
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Singleton
@PostgresRepositoryEnabled
public class PostgresLogRepository extends AbstractJdbcLogRepository {

    @Inject
    public PostgresLogRepository(@Named("logs") PostgresRepository<LogEntry> repository,
                                 QueueService queueService,
                                 JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }

    @Override
    protected Condition levelsCondition(List<Level> levels) {
        return PostgresLogRepositoryService.levelsCondition(levels);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, List<AbstractFilter<F>> filters, Map<F, String> fieldsMapping) {
        return PostgresLogRepositoryService.where(selectConditionStep, jdbcFilterService, filters, fieldsMapping);
    }


}
