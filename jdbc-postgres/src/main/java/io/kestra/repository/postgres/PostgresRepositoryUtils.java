package io.kestra.repository.postgres;

import io.kestra.core.utils.DateUtils;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

public final class PostgresRepositoryUtils {
    private PostgresRepositoryUtils() {
        // utility class pattern
    }

    public static Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("DATE_TRUNC('week', {0})", Date.class, DSL.field(dateField));
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
