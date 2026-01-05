package io.kestra.repository.mysql;

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

import java.util.Arrays;
import java.util.Date;

@Singleton
@MysqlRepositoryEnabled
public class MysqlLogRepository extends AbstractJdbcLogRepository {
    @Inject
    public MysqlLogRepository(@Named("logs") MysqlRepository<LogEntry> repository,
                              QueueService queueService,
                              JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(
            Arrays.asList("namespace", "flow_id", "task_id", "execution_id", "taskrun_id", "trigger_id", "message", "thread"),
            query
        );
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }
}

