package io.kestra.repository.postgres;

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
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Singleton
@PostgresRepositoryEnabled
public class PostgresLogRepository extends AbstractJdbcLogRepository {
    @Inject
    public PostgresLogRepository(@Named("logs") PostgresRepository<LogEntry> repository,
                                 JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }

    @Override
    protected Condition levelsCondition(List<Level> levels) {
        return DSL.condition("level in (" +
            levels
                .stream()
                .map(s -> "'" + s + "'::log_level")
                .collect(Collectors.joining(", ")) +
            ")");
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
