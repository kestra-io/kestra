package org.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.internal.lang3.LocaleUtils;
import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

public enum InstantHelper implements Helper<Object> {
    /**
     * <p>
     * Usage:
     * </p>
     *
     * <pre>
     *    {{instantFormat date ["format"] [format="format"][tz=timeZone|timeZoneId]}}
     * </pre>
     *
     * <p>
     * Format parameters is one of:
     * <ul>
     * <li>"full": full date format. For example: Tuesday, June 19, 2012</li>
     * <li>"long": long date format. For example: June 19, 2012</li>
     * <li>"medium": medium date format. For example: Jun 19, 2012</li>
     * <li>"short": short date format. For example: 6/19/12</li>
     * <li>"pattern": a date pattern.</li>
     * </ul>
     *
     * Otherwise, the default formatter will be used.
     * The format option can be specified as a parameter or hash (a.k.a named parameter).
     */
    instantFormat {
        /**
         * The default date styles.
         */
        private Map<String, FormatStyle> styles = ImmutableMap.of(
            "full", FormatStyle.FULL,
            "long", FormatStyle.LONG,
            "medium", FormatStyle.MEDIUM,
            "short", FormatStyle.SHORT
        );

        @Override
        public CharSequence apply(final Object value, final Options options) {
            Instant date;

            if (value instanceof Instant) {
                date = (Instant) value;
            } else {
                date = Instant.parse((String) value);
            }

            DateTimeFormatter formatter;

            String pattern = options.param(0, options.hash("format", "full"));

            FormatStyle style = styles.get(pattern);
            if (pattern.equals("")) {
                formatter = DateTimeFormatter.ofPattern(pattern);
            } else if (style == null) {
                formatter = DateTimeFormatter.ofPattern(pattern);
            } else {
                formatter = DateTimeFormatter.ofLocalizedDateTime(style);
            }

            String localeStr = options.param(1, Locale.getDefault().toString());
            Locale locale = LocaleUtils.toLocale(localeStr);
            formatter = formatter.withLocale(locale);

            Object tz = options.hash("tz");
            if (tz != null) {
                final ZoneId timeZone = tz instanceof ZoneId ? (ZoneId) tz : ZoneId.of(tz.toString());
                formatter = formatter.withZone(timeZone);
            } else {
                formatter = formatter.withZone(ZoneId.systemDefault());
            }

            return formatter.format(date);
        }
    },

    instantTimestamp {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            assert value instanceof Instant;
            Instant date = (Instant) value;

            return String.valueOf(date.getEpochSecond());
        }
    }
}

