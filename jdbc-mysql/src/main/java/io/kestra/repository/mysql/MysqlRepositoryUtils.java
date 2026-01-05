package io.kestra.repository.mysql;

import io.kestra.core.utils.DateUtils;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

public final class MysqlRepositoryUtils {
    private MysqlRepositoryUtils() {
        // utility class pattern
    }

    public static Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("STR_TO_DATE(CONCAT(YEARWEEK({0}, 3), ' Monday'), '%X%V %W')", Date.class, DSL.field(dateField));
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
