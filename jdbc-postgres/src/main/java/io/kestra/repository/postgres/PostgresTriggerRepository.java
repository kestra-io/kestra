package io.kestra.repository.postgres;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public PostgresTriggerRepository(@Named("triggers") PostgresRepository<Trigger> repository,
                                     JdbcFilterService filterService) {
        super(repository, filterService);
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
