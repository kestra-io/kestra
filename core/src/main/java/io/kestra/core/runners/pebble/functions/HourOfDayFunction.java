package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble function that returns the hour of day (0–23) of the given datetime string.
 *
 * <p>Accepts ISO 8601 datetime strings with or without a timezone offset
 * (e.g. {@code "2025-01-06T14:30:00Z"} or {@code "2025-01-06T14:30:00+02:00"} or
 * {@code "2025-01-06T14:30:00"}). The hour is taken from the local time component as written
 * in the string; no UTC normalization is performed. Plain date strings without a time
 * component are not supported.
 *
 * <p>Usage: {@code {{ hourOfDay(date) }}}
 *
 * @param date ISO 8601 datetime string (with or without timezone offset)
 */
public class HourOfDayFunction implements KestraFunction {
    public static final String NAME = "hourOfDay";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'hourOfDay()' function expects a 'date' argument.", lineNumber, self.getName());
        }

        String dateStr = dateArg.toString();

        try {
            return ZonedDateTime.parse(dateStr).getHour();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(dateStr).getHour();
            } catch (DateTimeParseException e2) {
                throw new PebbleException(e2, "The 'hourOfDay()' function could not parse 'date': " + e2.getMessage(), lineNumber, self.getName());
            }
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        return defaults;
    }
}
