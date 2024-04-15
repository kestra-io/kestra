package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class StartsWithFilter implements Filter {

    public static final String FILTER_NAME = "startsWith";

    private static final String ARGUMENT_VALUE = "value";

    private final static List<String> ARGS = List.of(ARGUMENT_VALUE);

    @Override
    public List<String> getArgumentNames() {
        return ARGS;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        String data = input.toString();

        return data.startsWith(args.get(ARGUMENT_VALUE).toString());
    }
}
