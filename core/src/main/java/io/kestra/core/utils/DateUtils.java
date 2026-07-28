package io.kestra.core.utils;

import java.time.*;
import java.util.List;
import java.util.Locale;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;

public class DateUtils {
    /**
     * @deprecated use {@link TypeConverter#toZonedDateTime(Object)} instead — same strict
     * ISO-8601 parsing, but throws an unchecked {@code TypeConversionException}.
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
        ZonedDateTime lowerDate = null;
        ZonedDateTime upperDate = null;
        // Extract the start/end bounds regardless of the comparison operation so a contradictory pair
        // (e.g. startDate == 2024 with endDate == 2023) is still rejected, not just GTE/LTE ranges.
        // A resource with a single timestamp expresses its window as two bounds on DATE, so there the
        // operation is what tells the two bounds apart.
        for (QueryFilter filter : filters) {
            if (QueryFilter.Field.START_DATE.equals(filter.field())) {
                startDate = TypeConverter.toZonedDateTime(filter.value());
            } else if (QueryFilter.Field.END_DATE.equals(filter.field())) {
                endDate = TypeConverter.toZonedDateTime(filter.value());
            } else if (QueryFilter.Field.DATE.equals(filter.field())) {
                switch (filter.operation()) {
                    case GREATER_THAN, GREATER_THAN_OR_EQUAL_TO -> lowerDate = TypeConverter.toZonedDateTime(filter.value());
                    case LESS_THAN, LESS_THAN_OR_EQUAL_TO -> upperDate = TypeConverter.toZonedDateTime(filter.value());
                    default -> {
                        // EQUALS/NOT_EQUALS carry no range to contradict.
                    }
                }
            }
        }
        validateTimeline(startDate, endDate);
        validateTimeline(lowerDate, upperDate);
    }

}
