package io.kestra.repository.h2;

import java.util.Date;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.utils.DateUtils;

public final class H2RepositoryUtils {
    private H2RepositoryUtils() {
        // utility class pattern
    }

    public static Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("FORMATDATETIME({0}, 'yyyy-MM')", Date.class, DSL.field(DSL.quotedName(dateField)));
            case WEEK:
                return DSL.field("DATE_TRUNC('WEEK', {0})", Date.class, DSL.field(DSL.quotedName(dateField)));
            case DAY:
                return DSL.field("FORMATDATETIME({0}, 'yyyy-MM-dd')", Date.class, DSL.field(DSL.quotedName(dateField)));
            case HOUR:
                return DSL.field("FORMATDATETIME({0}, 'yyyy-MM-dd HH:00:00')", Date.class, DSL.field(DSL.quotedName(dateField)));
            case MINUTE:
                return DSL.field("FORMATDATETIME({0}, 'yyyy-MM-dd HH:mm:00')", Date.class, DSL.field(DSL.quotedName(dateField)));
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}
