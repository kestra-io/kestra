package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KeysFilter implements Filter {
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
            return inputMap.keySet();
        }

        if (input instanceof List) {
            List inputList = (List) input;
            return IntStream
                .rangeClosed(0, inputList.size() - 1)
                .boxed()
                .toList();
        }

        if (input.getClass().isArray()) {
            int length = Array.getLength(input);
            return IntStream
                .rangeClosed(0, length - 1)
                .boxed()
                .toList();
        }

        throw new PebbleException(null, "'keys' filter can only be applied to List, Map, Array. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());

    }
}
