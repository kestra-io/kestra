package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UniqFilter implements Filter {

    @Override
    public List<String> getArgumentNames() {
        // No arguments are needed for the uniq filter
        return null;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context,
                        int lineNumber) throws PebbleException {
        // Check if the input is a list
        if (input instanceof List<?>) {
            List<?> list = (List<?>) input;

            // Deduplicate the list by using distinct stream operation
            return list.stream().distinct().collect(Collectors.toList());
        }

        // If the input is not a list, just return it as it is
        return input;
    }
}
