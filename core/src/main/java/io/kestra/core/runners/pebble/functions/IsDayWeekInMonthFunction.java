package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.utils.DateUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble function that returns {@code true} if the given date is the Nth occurrence of a weekday in its month.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code {{ isDayWeekInMonth(date, dayOfWeek, position) }}}</li>
 * </ul>
 *
 * @param date      any valid ISO 8601 date or datetime string
 * @param dayOfWeek day of the week (e.g. {@code "MONDAY"}, {@code "FRIDAY"})
 * @param position  occurrence within the month: {@code FIRST}, {@code SECOND}, {@code THIRD}, {@code FOURTH}, or {@code LAST}
 */
public class IsDayWeekInMonthFunction implements KestraFunction {
    public static final String NAME = "isDayWeekInMonth";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");
        Object dayOfWeekArg = args.get("dayOfWeek");
        Object positionArg = args.get("position");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'isDayWeekInMonth()' function expects a 'date' argument.", lineNumber, self.getName());
        }
        if (dayOfWeekArg == null) {
            throw new PebbleException(null, "The 'isDayWeekInMonth()' function expects a 'dayOfWeek' argument.", lineNumber, self.getName());
        }
        if (positionArg == null) {
            throw new PebbleException(null, "The 'isDayWeekInMonth()' function expects a 'position' argument.", lineNumber, self.getName());
        }

        LocalDate localDate;
        try {
            localDate = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'isDayWeekInMonth()' function could not parse 'date': " + e.getMessage(), lineNumber, self.getName());
        }

        DayOfWeek dayOfWeek;
        try {
            dayOfWeek = DayOfWeek.valueOf(dayOfWeekArg.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PebbleException(e, "The 'isDayWeekInMonth()' function received an invalid 'dayOfWeek' value: '" + dayOfWeekArg + "'. Expected one of: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.", lineNumber, self.getName());
        }

        LocalDate computed = switch (positionArg.toString().toUpperCase()) {
            case "FIRST" -> localDate.with(TemporalAdjusters.firstInMonth(dayOfWeek));
            case "SECOND" -> localDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).plusWeeks(1);
            case "THIRD" -> localDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).plusWeeks(2);
            case "FOURTH" -> localDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).plusWeeks(3);
            case "LAST" -> localDate.with(TemporalAdjusters.lastInMonth(dayOfWeek));
            default -> throw new PebbleException(null, "The 'isDayWeekInMonth()' function received an invalid 'position' value: '" + positionArg + "'. Expected one of: FIRST, SECOND, THIRD, FOURTH, LAST.", lineNumber, self.getName());
        };

        return computed.isEqual(localDate);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date", "dayOfWeek", "position");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        defaults.put("dayOfWeek", null);
        defaults.put("position", null);
        return defaults;
    }
}
