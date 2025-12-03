package io.kestra.repository.mysql;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.ScheduleContextInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.runner.JdbcSchedulerContext;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.List;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public MysqlTriggerRepository(@Named("triggers") MysqlRepository<Trigger> repository,
                                  QueueService queueService,
                                  JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("namespace", "flow_id", "trigger_id", "execution_id"), query);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }

    @Override
    protected Temporal toNextExecutionTime(ZonedDateTime now) {
        // next_execution_date in the table is stored in UTC
        // convert 'now' to UTC LocalDateTime to avoid any timezone/offset interpretation by the database.
        return now.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
