package io.kestra.repository.h2;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.utils.DateUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;
import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(@Named("executions") H2Repository<Execution> repository,
                                 ApplicationContext applicationContext,
                                 AbstractJdbcExecutorStateStorage executorStateStorage,
                                 JdbcFilterService filterService) {
        super(repository, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2ExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
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
