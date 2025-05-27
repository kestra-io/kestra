package io.kestra.repository.mysql;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
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
@MysqlRepositoryEnabled
public class MysqlTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public MysqlTriggerRepository(@Named("triggers") MysqlRepository<Trigger> repository,
                                  JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("namespace", "flow_id", "trigger_id", "execution_id"), query);
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
