package io.kestra.core.utils;

import io.kestra.core.exceptions.InternalException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
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

    public static GroupType groupByType(Long dayCount) {
        if (dayCount > 365) {
            return GroupType.MONTH;
        } else if (dayCount > 180) {
            return GroupType.WEEK;
        } else if (dayCount > 1) {
            return GroupType.DAY;
        } else {
            return GroupType.HOUR;
        }
    }

    public enum GroupType {
        MONTH,
        WEEK,
        DAY,
        HOUR;

        public String val() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
