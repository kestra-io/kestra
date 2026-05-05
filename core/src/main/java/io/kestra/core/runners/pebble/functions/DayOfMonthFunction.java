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
 * Pebble function that returns the day of the month (1–31) of the given date.
 *
 * <p>Usage: {@code {{ dayOfMonth(date) }}}
 *
 * @param date any valid ISO 8601 date or datetime string
 */
public class DayOfMonthFunction implements KestraFunction {
    public static final String NAME = "dayOfMonth";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'dayOfMonth()' function expects a 'date' argument.", lineNumber, self.getName());
        }

        LocalDate localDate;
        try {
            localDate = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'dayOfMonth()' function could not parse 'date': " + e.getMessage(), lineNumber, self.getName());
        }

        return localDate.getDayOfMonth();
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
