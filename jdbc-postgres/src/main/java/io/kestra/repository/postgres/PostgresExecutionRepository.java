package io.kestra.repository.postgres;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
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
import org.jooq.impl.SQLDataType;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Singleton
@PostgresRepositoryEnabled
public class PostgresExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public PostgresExecutionRepository(@Named("executions") PostgresRepository<Execution> repository,
                                       ApplicationContext applicationContext,
                                       AbstractJdbcExecutorStateStorage executorStateStorage,
                                       JdbcFilterService filterService) {
        super(repository, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition statesFilter(List<State.Type> state) {
        return DSL.or(state
            .stream()
            .map(Enum::name)
            .map(s -> DSL.field("state_current")
                .eq(DSL.field("CAST(? AS state_type)", SQLDataType.VARCHAR(50).getArrayType(), s)
                ))
            .toList()
        );
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return PostgresExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("TO_CHAR({0}, 'IYYY-IW')", Date.class, DSL.field(dateField));
            case DAY:
                return DSL.field("DATE({0})", Date.class, DSL.field(dateField));
            case HOUR:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM-DD HH24:00:00')", Date.class, DSL.field(dateField));
            case MINUTE:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM-DD HH24:MI:00')", Date.class, DSL.field(dateField));
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}
