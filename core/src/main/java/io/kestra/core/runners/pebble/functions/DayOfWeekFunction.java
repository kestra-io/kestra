package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.utils.DateUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble function that returns the day-of-week name of the given date as an uppercase string
 * (e.g. {@code "MONDAY"}, {@code "TUESDAY"}, …, {@code "SUNDAY"}).
 *
 * <p>Prefer this over {@code date | date('EEEE')} when a locale-independent, machine-readable
 * day name is needed (for example, to compare against another value or pass to a condition).
 *
 * <p>Usage: {@code {{ dayOfWeek(date) }}}
 *
 * <p>Note: the day name is derived from the local date component of the input string;
 * no UTC normalization is performed. A datetime string such as {@code "2025-01-06T00:30:00+02:00"}
 * yields {@code "MONDAY"} (Jan 6), not {@code "SUNDAY"} (Jan 5 in UTC).
 *
 * @param date any valid ISO 8601 date or datetime string
 */
public class DayOfWeekFunction implements KestraFunction {
    public static final String NAME = "dayOfWeek";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'dayOfWeek()' function expects a 'date' argument.", lineNumber, self.getName());
        }

        LocalDate localDate;
        try {
            localDate = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'dayOfWeek()' function could not parse 'date': " + e.getMessage(), lineNumber, self.getName());
        }

        return localDate.getDayOfWeek().name();
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
