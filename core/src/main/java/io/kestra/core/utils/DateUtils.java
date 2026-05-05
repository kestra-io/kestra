package io.kestra.core.utils;

import java.time.*;
import java.util.List;
import java.util.Locale;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;

public class DateUtils {
    public static ZonedDateTime parseZonedDateTime(String render) throws InternalException {
        ZonedDateTime currentDate;
        try {
            currentDate = ZonedDateTime.parse(render);
        } catch (DateTimeException e) {
            throw new InternalException(e);
        }
        return currentDate;
    }

    public static OffsetTime parseOffsetTime(String render) throws InternalException {
        OffsetTime currentTime;
        try {
            currentTime = OffsetTime.parse(render);
        } catch (DateTimeException e) {
            throw new InternalException(e);
        }
        return currentTime;
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

    /**
     * Parses an ISO 8601 date or datetime string to an {@link Instant}.
     *
     * <p>Parsing order: {@link ZonedDateTime} → {@link LocalDateTime} (treated as UTC) →
     * {@link LocalDate} (treated as midnight UTC). Throws {@link InternalException} if none match.
     */
    public static Instant parseInstant(String render) throws InternalException {
        try {
            return ZonedDateTime.parse(render).toInstant();
        } catch (DateTimeException e1) {
            try {
                return LocalDateTime.parse(render).toInstant(ZoneOffset.UTC);
            } catch (DateTimeException e2) {
                try {
                    return LocalDate.parse(render).atStartOfDay(ZoneOffset.UTC).toInstant();
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
                startDate = parse(filter.value());
            } else if (isEndDateFilter(filter)) {
                endDate = parse(filter.value());
            }
        }
        validateTimeline(startDate, endDate);
    }

    private static ZonedDateTime parse(Object o) {
        if (o instanceof ZonedDateTime) {
            return (ZonedDateTime) o;
        } else {
            return ZonedDateTime.parse(o.toString());
        }
    }

    private static boolean isEndDateFilter(QueryFilter filter) {
        return (filter.operation().equals(QueryFilter.Op.LESS_THAN) && filter.field().equals(QueryFilter.Field.END_DATE))
            || (filter.operation().equals(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO) && filter.field().equals(QueryFilter.Field.END_DATE));
    }

    private static boolean isStartDateFilter(QueryFilter filter) {
        return (filter.operation().equals(QueryFilter.Op.GREATER_THAN) && filter.field().equals(QueryFilter.Field.START_DATE))
            || (filter.operation().equals(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO) && filter.field().equals(QueryFilter.Field.START_DATE));
    }
}
