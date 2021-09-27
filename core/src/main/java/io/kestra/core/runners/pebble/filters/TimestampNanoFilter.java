package io.kestra.core.runners.pebble.filters;

import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.kestra.core.runners.pebble.AbstractDate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TimestampNanoFilter extends AbstractDate implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return List.of("timeZone", "existingFormat");
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        final String timeZone = (String) args.get("timeZone");
        final String existingFormat = (String) args.get("existingFormat");

        ZoneId zoneId = zoneId(timeZone);
        ZonedDateTime date = convert(input, zoneId, existingFormat);

        return String.valueOf(TimeUnit.SECONDS.toNanos(date.toEpochSecond()) + date.getNano());
    }
}
