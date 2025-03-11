package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.pebbletemplates.pebble.extension.Filter;
import java.util.List;
import java.util.Map;

public class StringFilter implements Filter {

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) return null;

        if (input instanceof String) return input;

        return input.toString();
    }

    @Override
    public List<String> getArgumentNames() {
        return null;
    }
}
