package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.internal.lang3.LocaleUtils;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public enum DateHelper implements Helper<Object> {
    /**
     * <p>
     * Usage:
     * </p>
     *
     * <pre>
     *    {{dateFormat date ["format"] [format="format"][tz=timeZone|timeZoneId]}}
     * </pre>
     *
     * <p>
     * Format parameters is one of:
     * <ul>
     * <li>"full": Sunday, September 8, 2013 at 4:19:12 PM Central European Summer Time
     * <li>"long": September 8, 2013 at 4:19:12 PM CEST
     * <li>"medium": Sep 8, 2013, 4:19:12 PM
     * <li>"short": 9/8/13, 4:19 PM
     * <li>"iso": 2013-09-08T16:19:12.000000+02:00
     * <li>"iso_sec": 2013-09-08T16:19:12+02:00
     * <li>"sql": 2013-09-08 16:19:12.000000
     * <li>"sql_seq": 2013-09-08 16:19:12
     * <li>"iso_date_time": 2013-09-08T16:19:12+02:00[Europe/Paris]
     * <li>"iso_date": 2013-09-08+02:00
     * <li>"iso_time": 16:19:12+02:00
     * <li>"iso_local_date": 2013-09-08
     * <li>"iso_instant": 2013-09-08T14:19:12Z
     * <li>"iso_local_date_time": 2013-09-08T16:19:12
     * <li>"iso_local_time": 16:19:12
     * <li>"iso_offset_time": 16:19:12+02:00
     * <li>"iso_ordinal_date": 2013-251+02:00
     * <li>"iso_week_date": 2013-W36-7+02:00
     * <li>"iso_zoned_date_time": 2013-09-08T16:19:12+02:00[Europe/Paris]
     * <li>"rfc_1123_date_time": Sun, 8 Sep 2013 16:19:12 +0200
     * <li>"pattern": a date pattern.</li>
     * </ul>
     *
     * Otherwise, the default formatter will be used.
     * The format option can be specified as a parameter or hash (a.k.a named parameter).
     */
    dateFormat {


        @Override
        public CharSequence apply(final Object value, final Options options) {
            ZoneId zoneId = DateHelper.zoneId(options);
            ZonedDateTime date = zonedDateTime(value, zoneId);
            String format = options.param(0, options.hash("format", "iso"));
            String localeStr = options.param(1, Locale.getDefault().toString());

            return format(date, format, zoneId, localeStr);
        }
    },

    now {
        @Override
        public Object apply(final Object value, final Options options) throws IOException {
            return DateHelper.dateFormat.apply(ZonedDateTime.now(DateHelper.zoneId(options)), options);
        }
    },

    timestamp {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            return String.valueOf(zonedDateTime(value, DateHelper.zoneId(options)).toEpochSecond());
        }
    },

    nanotimestamp {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            ZonedDateTime date = zonedDateTime(value, DateHelper.zoneId(options));

            return String.valueOf(TimeUnit.SECONDS.toNanos(date.toEpochSecond()) + date.getNano());
        }
    },

    microtimestamp {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            ZonedDateTime date = zonedDateTime(value, DateHelper.zoneId(options));

            return String.valueOf(TimeUnit.SECONDS.toNanos(date.toEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(date.getNano()));
        }
    },

    dateAdd {
        @Override
        public Object apply(final Object value, final Options options) throws IOException {
            ZonedDateTime date = zonedDateTime(value, DateHelper.zoneId(options));
            ZoneId zoneId = DateHelper.zoneId(options);

            Integer amount = options.param(0, options.hash("amount"));
            String unit = options.param(1, options.hash("unit"));
            String format = options.param(2, options.hash("format", "iso"));
            String localeStr = options.param(2, Locale.getDefault().toString());

            ZonedDateTime plus = date.plus(amount, ChronoUnit.valueOf(unit));

            return format(plus, format, zoneId, localeStr);
        }
    };

    /**
     * The default date styles.
     */
    private static final Map<String, FormatStyle> styles = ImmutableMap.of(
        "full", FormatStyle.FULL,
        "long", FormatStyle.LONG,
        "medium", FormatStyle.MEDIUM,
        "short", FormatStyle.SHORT
    );

    /**
     * The default date formatters
     */
    private static final Map<String, DateTimeFormatter> formatters = ImmutableMap.<String, DateTimeFormatter>builder()
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

    private static String format(ZonedDateTime date, String format, ZoneId zoneId, String localeStr) {
        DateTimeFormatter formatter;

        DateTimeFormatter formatterFind = formatters.get(format);
        FormatStyle styleFind = styles.get(format);
        if (styleFind != null) {
            formatter = DateTimeFormatter.ofLocalizedDateTime(styleFind);
        } else if (formatterFind != null) {
            formatter = formatterFind;
        } else {
            formatter = DateTimeFormatter.ofPattern(format);
        }

        Locale locale = LocaleUtils.toLocale(localeStr);
        formatter = formatter.withLocale(locale);

        formatter = formatter.withZone(zoneId);

        return formatter.format(date);
    }

    private static ZonedDateTime zonedDateTime(Object value, ZoneId zoneId) {
        ZonedDateTime date;

        if (value instanceof Date) {
            date = ZonedDateTime.ofInstant(((Date) value).toInstant(), zoneId);
        } else if (value instanceof Instant) {
            date = ((Instant) value).atZone(zoneId);
        } else if (value instanceof LocalDateTime) {
            date = ZonedDateTime.of((LocalDateTime) value, zoneId);
        } else if (value instanceof LocalDate) {
            date = ZonedDateTime.of((LocalDate) value, LocalTime.NOON, zoneId);
        } else if (value instanceof ZonedDateTime) {
            date = (ZonedDateTime) value;
        } else {
            date = ZonedDateTime.parse((String) value);
        }

        return date;
    }

    private static ZoneId zoneId(Options options) {

        Object tz = options.hash("tz");
        if (tz != null) {
            return tz instanceof ZoneId ? (ZoneId) tz : ZoneId.of(tz.toString());
        } else {
            return ZoneId.systemDefault();
        }
    }
}

