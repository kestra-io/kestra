package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ValuesFilter implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (input instanceof Map) {
            Map inputMap = (Map) input;
            return inputMap.values();
        }

        throw new PebbleException(null, "'values' filter can only be applied to Map. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());
    }
}
