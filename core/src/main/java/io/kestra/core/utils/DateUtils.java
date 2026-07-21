package io.kestra.core.utils;

import java.time.*;
import java.util.List;
import java.util.Locale;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;

public class DateUtils {
    /**
     * @deprecated use {@link TypeConverter#toZonedDateTime(Object)} instead — same strict
     *             ISO-8601 parsing, but throws an unchecked {@code TypeConversionException}.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static ZonedDateTime parseZonedDateTime(String render) throws InternalException {
        ZonedDateTime currentDate;
        try {
            currentDate = ZonedDateTime.parse(render);
        } catch (DateTimeException e) {
            throw new InternalException(e);
        }
        return currentDate;
    }

    public static LocalDate parseLocalDate(String render) throws InternalException {
        try {
            return LocalDate.parse(render);
        } catch (DateTimeException e1) {
            try {
                return ZonedDateTime.parse(render).toLocalDate();
            } catch (DateTimeException e2) {
                try {
                    return LocalDateTime.parse(render).toLocalDate();
                } catch (DateTimeException e3) {
                    throw new InternalException(e3);
                }
            }
        }
    }

    public static GroupType groupByType(Duration duration) {
        if (duration.toDays() > GroupValue.MONTH.getValue()) {
            return GroupType.MONTH;
        } else if (duration.toDays() > GroupValue.WEEK.getValue()) {
            return GroupType.WEEK;
        } else if (duration.toDays() > GroupValue.DAY.getValue()) {
            return GroupType.DAY;
        } else if (duration.toHours() > GroupValue.HOUR.getValue()) {
            return GroupType.HOUR;
        } else {
            return GroupType.MINUTE;
        }
    }

    public enum GroupType {
        MONTH,
        WEEK,
        DAY,
        HOUR,
        MINUTE;

        public String val() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum GroupValue {
        MONTH(365),
        WEEK(180),
        DAY(1),
        HOUR(6);

        private final int value;

        GroupValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static void validateTimeline(ZonedDateTime startDate, ZonedDateTime endDate) {
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Start date must be before End Date");
            }
        }
    }

    public static void validateTimeline(List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        ZonedDateTime startDate = null;
        ZonedDateTime endDate = null;
        for (QueryFilter filter : filters) {
            if (isStartDateFilter(filter)) {
                startDate = TypeConverter.toZonedDateTime(filter.value());
            } else if (isEndDateFilter(filter)) {
                endDate = TypeConverter.toZonedDateTime(filter.value());
            }
        }
        validateTimeline(startDate, endDate);
    }

    private static boolean isEndDateFilter(QueryFilter filter) {
        return QueryFilter.Field.END_DATE.equals(filter.field())
            && (QueryFilter.Op.LESS_THAN.equals(filter.operation())
                || QueryFilter.Op.LESS_THAN_OR_EQUAL_TO.equals(filter.operation()));
    }

    private static boolean isStartDateFilter(QueryFilter filter) {
        return QueryFilter.Field.START_DATE.equals(filter.field())
            && (QueryFilter.Op.GREATER_THAN.equals(filter.operation())
                || QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO.equals(filter.operation()));
    }
}
