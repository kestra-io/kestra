package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReplaceFilter implements Filter {

    public static final String FILTER_NAME = "replace";

    private static final String ARGUMENT_PAIRS = "replace_pairs";
    private static final String ARGUMENT_REGEXP = "regexp";

    private final static List<String> ARGS = List.of(ARGUMENT_PAIRS, ARGUMENT_REGEXP);

    @Override
    public List<String> getArgumentNames() {
        return ARGS;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        String data = input.toString();
        if (args.get(ARGUMENT_PAIRS) == null) {
            throw new PebbleException(null,
                MessageFormat.format("The argument ''{0}'' is required.", ARGUMENT_PAIRS), lineNumber,
                self.getName()
            );
        }

        final boolean regexp = args.containsKey(ARGUMENT_REGEXP) ? (Boolean) args.get(ARGUMENT_REGEXP) : false;

        Map<?, ?> replacePair = (Map<?, ?>) args.get(ARGUMENT_PAIRS);

        for (Map.Entry<?, ?> entry : replacePair.entrySet()) {
            if (regexp) {
                data = data.replaceAll(entry.getKey().toString(), entry.getValue().toString());
            } else {
                data = data.replace(entry.getKey().toString(), entry.getValue().toString());
            }
        }

        return data;
    }
}
