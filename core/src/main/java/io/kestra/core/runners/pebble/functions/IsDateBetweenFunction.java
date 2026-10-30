package io.kestra.core.runners.pebble.functions;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.TypeConversionException;
import io.kestra.core.utils.TypeConverter;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Pebble function that returns {@code true} if the given datetime is strictly after {@code after}
 * and strictly before {@code before}.
 *
 * <p>
 * The three values are compared as instants, so datetimes written with different offsets compare
 * correctly. Each value must be an ISO 8601 datetime with a timezone offset (e.g.
 * {@code "2026-01-01T00:00:00Z"} or {@code "2026-01-01T00:00:00-03:00"}); {@code date} may also be
 * a {@link java.time.ZonedDateTime}, which is what {@code trigger.date} is inside a Schedule
 * {@code when} condition.
 *
 * <p>
 * Usage: {@code {{ isDateBetween(trigger.date, '2025-12-31T23:59:59Z', '2026-06-30T23:59:59Z') }}}
 *
 * @param date the datetime to check
 * @param after the exclusive lower bound
 * @param before the exclusive upper bound
 */
public class IsDateBetweenFunction implements KestraFunction {
    public static final String NAME = "isDateBetween";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Instant date = toInstant(args, "date", self, lineNumber);
        Instant after = toInstant(args, "after", self, lineNumber);
        Instant before = toInstant(args, "before", self, lineNumber);

        return date.isAfter(after) && date.isBefore(before);
    }

    private static Instant toInstant(Map<String, Object> args, String name, PebbleTemplate self, int lineNumber) {
        Object value = args.get(name);

        if (value == null) {
            throw new PebbleException(null, "The 'isDateBetween()' function expects a '" + name + "' argument.", lineNumber, self.getName());
        }

        try {
            return TypeConverter.toZonedDateTime(value).toInstant();
        } catch (TypeConversionException e) {
            throw new PebbleException(e, "The 'isDateBetween()' function could not parse '" + name + "': " + e.getMessage(), lineNumber, self.getName());
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date", "after", "before");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        defaults.put("after", null);
        defaults.put("before", null);
        return defaults;
    }
}
