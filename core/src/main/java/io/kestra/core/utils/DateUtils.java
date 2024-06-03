package io.kestra.core.utils;

import io.kestra.core.exceptions.InternalException;

import java.time.*;
import java.util.Locale;

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
        LocalDate currentDate;
        try {
            currentDate = LocalDate.parse(render);
        } catch (DateTimeException e) {
            currentDate = DateUtils.parseZonedDateTime(render).toLocalDate();
        }

        return currentDate;
    }

    public static GroupType groupByType(Duration duration) {
        if (duration.toDays() > GroupValue.MONTH.getValue()) {
            return GroupType.MONTH;
        } else if (duration.toDays() > GroupValue.WEEK.getValue()) {
            return GroupType.WEEK;
        } else if (duration.toDays() > GroupValue.DAY.getValue()) {
            return GroupType.DAY;
        } else if (duration.toHours() > GroupValue.HOUR.getValue()){
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
}
