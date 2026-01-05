package io.kestra.repository.h2;

import io.kestra.core.utils.DateUtils;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

public final class H2RepositoryUtils {
    private H2RepositoryUtils() {
        // utility class pattern
    }

    public static Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM')", Date.class);
            case WEEK:
                return DSL.field("DATE_TRUNC('WEEK', \"" + dateField + "\")", Date.class);
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
