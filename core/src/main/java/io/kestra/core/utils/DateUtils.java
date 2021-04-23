package io.kestra.core.utils;

import io.kestra.core.exceptions.InternalException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZonedDateTime;

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

    public static LocalDate parseLocalDate(String render) throws InternalException {
        LocalDate currentDate;
        try {
            currentDate = LocalDate.parse(render);
        } catch (DateTimeException e) {
            currentDate = DateUtils.parseZonedDateTime(render).toLocalDate();
        }

        return currentDate;
    }
}
