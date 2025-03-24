package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DistinctFilter implements Filter {

    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context,
                        int lineNumber) throws PebbleException {
								
		if (input == null) {
            return "null";
        }						
							
        // Check if the input is a list
        if (input instanceof List<?>) {
            List<?> list = (List<?>) input;

            // Deduplicate the list by using distinct stream operation
            return list.stream().distinct().collect(Collectors.toList());
        }

        //if the input is not list, throwing exception with constructor
        throw new PebbleException(null,
            "Input must be a list, but received : " + (input != null ? input.getClass().getName() : "null"),
            lineNumber,
            self != null ? self.getName() : "Unknown");
    }
}
