package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.utils.DateUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble function that returns {@code true} if the first date is strictly before the second.
 *
 * <p>Both arguments accept any ISO 8601 date or datetime string. Timezone-aware datetimes are
 * compared at the instant level; timezone-naive datetimes and plain dates are treated as UTC.
 *
 * <p>Usage: {@code {{ isDateBefore(date, reference) }}}
 *
 * @param date      any valid ISO 8601 date or datetime string
 * @param reference any valid ISO 8601 date or datetime string to compare against
 */
public class IsDateBeforeFunction implements KestraFunction {
    public static final String NAME = "isDateBefore";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");
        Object referenceArg = args.get("reference");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'isDateBefore()' function expects a 'date' argument.", lineNumber, self.getName());
        }
        if (referenceArg == null) {
            throw new PebbleException(null, "The 'isDateBefore()' function expects a 'reference' argument.", lineNumber, self.getName());
        }

        try {
            return DateUtils.parseInstant(dateArg.toString()).isBefore(DateUtils.parseInstant(referenceArg.toString()));
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'isDateBefore()' function could not parse a date argument: " + e.getMessage(), lineNumber, self.getName());
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date", "reference");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        defaults.put("reference", null);
        return defaults;
    }
}
