package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.utils.DateUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pebble function that returns {@code true} if the given date is the last working day of its month.
 *
 * <p>A working day is Monday–Friday by default. Pass a {@code workingDays} argument (comma-separated
 * or space-separated day names) to override which days count as working days.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code {{ isLastWorkingDay(date) }}}</li>
 *   <li>{@code {{ isLastWorkingDay(date, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY') }}}</li>
 * </ul>
 *
 * @param date        any valid ISO 8601 date or datetime string
 * @param workingDays optional comma- or space-separated list of day names (e.g. {@code "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"})
 */
public class IsLastWorkingDayFunction implements KestraFunction {
    public static final String NAME = "isLastWorkingDay";

    private static final Set<DayOfWeek> DEFAULT_WORKING_DAYS = EnumSet.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'isLastWorkingDay()' function expects a 'date' argument.", lineNumber, self.getName());
        }

        LocalDate localDate;
        try {
            localDate = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'isLastWorkingDay()' function could not parse 'date': " + e.getMessage(), lineNumber, self.getName());
        }

        Set<DayOfWeek> workingDays = resolveWorkingDays(args.get("workingDays"), self, lineNumber);

        if (!workingDays.contains(localDate.getDayOfWeek())) {
            return false;
        }

        // Walk backwards from the last calendar day of the month to find the last working day
        LocalDate lastOfMonth = localDate.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate candidate = lastOfMonth;
        while (!workingDays.contains(candidate.getDayOfWeek())) {
            candidate = candidate.minusDays(1);
        }

        return localDate.isEqual(candidate);
    }

    private Set<DayOfWeek> resolveWorkingDays(Object workingDaysArg, PebbleTemplate self, int lineNumber) {
        if (workingDaysArg == null || workingDaysArg.toString().isBlank()) {
            return DEFAULT_WORKING_DAYS;
        }

        String[] parts = workingDaysArg.toString().split("[,\\s]+");
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(DayOfWeek.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new PebbleException(e,
                    "The 'isLastWorkingDay()' function received an invalid 'workingDays' value: '" + trimmed + "'. Expected day names like MONDAY, TUESDAY, etc.",
                    lineNumber, self.getName());
            }
        }

        if (result.isEmpty()) {
            throw new PebbleException(null, "The 'isLastWorkingDay()' function requires at least one valid day in 'workingDays'.", lineNumber, self.getName());
        }

        return result;
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date", "workingDays");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        defaults.put("workingDays", null);
        return defaults;
    }
}
