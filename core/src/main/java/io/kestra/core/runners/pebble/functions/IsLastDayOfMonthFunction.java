package io.kestra.core.runners.pebble.functions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.utils.DateUtils;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Returns whether a date is the last calendar day of its month, including weekends and leap days.
 * Date-time values use their local date without conversion to UTC.
 */
public class IsLastDayOfMonthFunction implements KestraFunction {
    public static final String NAME = "isLastDayOfMonth";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");
        if (dateArg == null) {
            throw new PebbleException(null, "The 'isLastDayOfMonth()' function expects a 'date' argument.", lineNumber, self.getName());
        }

        LocalDate date;
        try {
            date = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'isLastDayOfMonth()' function could not parse date '%s'.".formatted(dateArg), lineNumber, self.getName());
        }
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        return defaults;
    }
}
