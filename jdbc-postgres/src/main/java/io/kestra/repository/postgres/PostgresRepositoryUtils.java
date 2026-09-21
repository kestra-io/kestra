package io.kestra.repository.postgres;

import java.sql.Timestamp;
import java.util.Date;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.utils.DateUtils;

public final class PostgresRepositoryUtils {
    private PostgresRepositoryUtils() {
        // utility class pattern
    }

    /**
     * Renders {@code dateField} as a UTC wall-clock timestamp, for SQL-side date-part extraction.
     * <p>
     * Postgres date columns are {@code TIMESTAMPTZ}, which stores an absolute instant. Casting one to
     * {@code timestamp} resolves it in the session timezone, and pgjdbc sets the session timezone
     * from the JVM default — so a plain cast yields local, not UTC, date parts. {@code AT TIME ZONE
     * 'UTC'} pins the conversion instead of inheriting it from the connection.
     */
    public static Field<Timestamp> utcTimestampField(String dateField) {
        return DSL.field("({0} AT TIME ZONE 'UTC')", Timestamp.class, DSL.field(DSL.quotedName(dateField)));
    }

    public static Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("DATE_TRUNC('month', {0})", Date.class, DSL.field(dateField));
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
