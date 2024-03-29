package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.kestra.core.runners.pebble.AbstractDate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class DateAddFilter extends AbstractDate implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return List.of("amount", "unit", "format", "timeZone", "existingFormat", "locale");
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        final Long amount = (Long) args.get("amount");
        final String unit = (String) args.get("unit");
        final String timeZone = (String) args.get("timeZone");
        final String existingFormat = (String) args.get("existingFormat");

        ZoneId zoneId = zoneId(timeZone);
        ZonedDateTime date = convert(input, zoneId, existingFormat);

        ZonedDateTime plus = date.plus(amount, ChronoUnit.valueOf(unit));

        return format(plus, args, context);
    }
}
