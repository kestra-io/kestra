package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public MysqlExecutionRepository(@Named("executions") MysqlRepository<Execution> repository,
                                    ApplicationContext applicationContext,
                                    AbstractJdbcExecutorStateStorage executorStateStorage,
                                    JdbcFilterService filterService) {
        super(repository, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return MysqlExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
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
