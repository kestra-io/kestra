package io.kestra.core.runners.pebble;

import com.google.common.collect.ImmutableMap;
import io.pebbletemplates.pebble.template.EvaluationContext;
import org.apache.commons.lang3.LocaleUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.*;

public abstract class AbstractDate {
    public List<String> getArgumentNames() {
        return List.of("format", "timeZone", "existingFormat", "locale");
    }

    private static final Map<String, DateTimeFormatter> FORMATTERS = ImmutableMap.<String, DateTimeFormatter>builder()
        .put("iso", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"))
        .put("iso_sec", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
        .put("sql", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"))
        .put("sql_seq", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .put("iso_date_time", DateTimeFormatter.ISO_DATE_TIME)
        .put("iso_date", DateTimeFormatter.ISO_DATE)
        .put("iso_time", DateTimeFormatter.ISO_TIME)
        .put("iso_local_date", DateTimeFormatter.ISO_LOCAL_DATE)
        .put("iso_instant", DateTimeFormatter.ISO_INSTANT)
        .put("iso_local_date_time", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .put("iso_local_time", DateTimeFormatter.ISO_LOCAL_TIME)
        .put("iso_offset_time", DateTimeFormatter.ISO_OFFSET_TIME)
        .put("iso_ordinal_date", DateTimeFormatter.ISO_ORDINAL_DATE)
        .put("iso_week_date", DateTimeFormatter.ISO_WEEK_DATE)
        .put("iso_zoned_date_time", DateTimeFormatter.ISO_ZONED_DATE_TIME)
        .put("rfc_1123_date_time", DateTimeFormatter.RFC_1123_DATE_TIME)
        .build();

    private static final Map<String, FormatStyle> STYLES = ImmutableMap.of(
        "full", FormatStyle.FULL,
        "long", FormatStyle.LONG,
        "medium", FormatStyle.MEDIUM,
        "short", FormatStyle.SHORT
    );

    protected static String format(Object input, Map<String, Object> args, EvaluationContext context) {
        final String format = args.containsKey("format") ? (String) args.get("format") : "iso";
        final String timeZone = (String) args.get("timeZone");
        final String existingFormat = (String) args.get("existingFormat");
        final Locale locale = args.containsKey("locale") ? LocaleUtils.toLocale((String) args.get("locale")) : context.getLocale();

        ZoneId zoneId = zoneId(timeZone);
        ZonedDateTime date = convert(input, zoneId, existingFormat);

        DateTimeFormatter formatter = formatter(format)
            .withLocale(locale)
            .withZone(zoneId);

        return formatter.format(date);
    }

    private static DateTimeFormatter formatter(String format) {
        DateTimeFormatter formatterFind = FORMATTERS.get(format);
        FormatStyle styleFind = STYLES.get(format);

        if (styleFind != null) {
            return DateTimeFormatter.ofLocalizedDateTime(styleFind);
        } else if (formatterFind != null) {
            return formatterFind;
        } else {
            return DateTimeFormatter.ofPattern(format);
        }
    }

    protected static ZoneId zoneId(String input) {
        if (input != null) {
            return ZoneId.of(input);
        } else {
            return ZoneId.systemDefault();
        }
    }

    protected static ZonedDateTime convert(Object value, ZoneId zoneId, String existingFormat) {
        if (value instanceof Date) {
            return ZonedDateTime.ofInstant(((Date) value).toInstant(), zoneId);
        }

        if (value instanceof Instant) {
            return ((Instant) value).atZone(zoneId);
        }

        if (value instanceof LocalDateTime) {
            return ZonedDateTime.of((LocalDateTime) value, zoneId);
        }

        if (value instanceof LocalDate) {
            return ZonedDateTime.of((LocalDate) value, LocalTime.NOON, zoneId);
        }

        if (value instanceof ZonedDateTime) {
            return (ZonedDateTime) value;
        }

        if (value instanceof Long) {
            return Instant.ofEpochSecond((Long) value).atZone(zoneId);
        }

        try {
            if (existingFormat != null) {
                return ZonedDateTime.parse((String) value, formatter(existingFormat));
            } else {
                return ZonedDateTime.parse((String) value);
            }
        } catch (DateTimeParseException e) {
            try {
                if (existingFormat != null) {
                    return LocalDateTime.parse((String) value, formatter(existingFormat)).atZone(zoneId);
                } else {
                    return LocalDateTime.parse((String) value).atZone(zoneId);
                }
            } catch (DateTimeParseException e2) {
                if (existingFormat != null) {
                    return LocalDate.parse((String) value, formatter(existingFormat)).atStartOfDay().atZone(zoneId);
                } else {
                    return LocalDate.parse((String) value).atStartOfDay().atZone(zoneId);
                }
            }
        }
    }
}
