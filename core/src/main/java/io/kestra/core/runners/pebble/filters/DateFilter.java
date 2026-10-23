package io.kestra.core.runners.pebble.filters;

import java.time.DateTimeException;
import java.util.Map;

import io.kestra.core.runners.pebble.AbstractDate;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class DateFilter extends AbstractDate implements Filter {
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return "";
        }

        try {
            return format(input, args, context);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self != null ? self.getName() : "Unknown");
        }
    }
}
