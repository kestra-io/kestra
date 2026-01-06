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
import org.jooq.Record;
import org.slf4j.event.Level;
import java.time.ZonedDateTime;

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

    public LogEntry map(Record record) {
        return LogEntry.builder()
            .id(record.get("id", String.class))
            .tenantId(record.get("tenant_id", String.class))
            .namespace(record.get("namespace", String.class))
            .flowId(record.get("flow_id", String.class))
            .taskId(record.get("task_id", String.class))
            .executionId(record.get("execution_id", String.class))
            .taskRunId(record.get("taskrun_id", String.class))
            .attemptNumber(record.get("attempt_number", Integer.class))
            .triggerId(record.get("trigger_id", String.class))
            .timestamp(record.get("timestamp", ZonedDateTime.class).toInstant())
            .level(Level.valueOf(record.get("level", String.class)))
            .thread(record.get("thread", String.class))
            .message(record.get("message", String.class))
            .deleted(record.get("deleted", Boolean.class))
            .build();
    }
}