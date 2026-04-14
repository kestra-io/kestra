package io.kestra.core.runners.pebble.functions;

import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
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
 * Pebble function that returns {@code true} if the given date is a public holiday for the specified country/sub-division.
 * Backed by the <a href="https://github.com/focus-shift/jollyday">Jollyday</a> library (supports more than 70 countries).
 *
 * <p>The {@code countryCode} argument is required; there is no locale-based fallback.
 * The {@code subDivision} argument is optional — omit it to check country-level holidays only.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code {{ isPublicHoliday(date, countryCode) }}}</li>
 *   <li>{@code {{ isPublicHoliday(date, countryCode, subDivision) }}}</li>
 * </ul>
 *
 * @param date        any valid ISO 8601 date or datetime string
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g. {@code "FR"})
 * @param subDivision optional ISO 3166-2 sub-division code (e.g. {@code "IDF"})
 */
public class IsPublicHolidayFunction implements KestraFunction {
    public static final String NAME = "isPublicHoliday";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object dateArg = args.get("date");
        Object countryCodeArg = args.get("countryCode");
        Object subDivisionArg = args.get("subDivision");

        if (dateArg == null) {
            throw new PebbleException(null, "The 'isPublicHoliday()' function expects a 'date' argument.", lineNumber, self.getName());
        }
        if (countryCodeArg == null) {
            throw new PebbleException(null, "The 'isPublicHoliday()' function expects a 'countryCode' argument.", lineNumber, self.getName());
        }

        String countryCode = countryCodeArg.toString();
        String subDivision = subDivisionArg != null && !subDivisionArg.toString().isBlank()
            ? subDivisionArg.toString()
            : null;

        LocalDate localDate;
        try {
            localDate = DateUtils.parseLocalDate(dateArg.toString());
        } catch (InternalException e) {
            throw new PebbleException(e, "The 'isPublicHoliday()' function could not parse 'date': " + e.getMessage(), lineNumber, self.getName());
        }

        HolidayManager holidayManager = HolidayManager.getInstance(ManagerParameters.create(countryCode));

        return subDivision == null
            ? holidayManager.isHoliday(localDate)
            : holidayManager.isHoliday(localDate, subDivision);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("date", "countryCode", "subDivision");
    }

    @Override
    // HashMap is required here because Map.of() does not allow null values,
    // and null defaults indicate arguments with no meaningful autocompletion default.
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("date", null);
        defaults.put("countryCode", null);
        defaults.put("subDivision", null);
        return defaults;
    }
}
