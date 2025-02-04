package io.kestra.repository.mysql;

import io.kestra.core.models.executions.LogEntry;
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
                              JdbcFilterService filterService) {
        super(repository, filterService);
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

