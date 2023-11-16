package io.kestra.core.runners.pebble.filters;


import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlattenFilter implements Filter {
    private final List<String> argumentNames = new ArrayList<>();

    @Override
    public List<String> getArgumentNames() {
        return this.argumentNames;
    }


    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!(input instanceof List)) {
            throw new PebbleException(null, "The 'flatten' filter can only be applied to lists.", lineNumber, self.getName());
        }

        try {
            List<?> list = (List<?>) input;
            List<Object> flattened = list.stream()
                .flatMap(o -> o instanceof List ? ((List<?>) o).stream() : Stream.of(o))
                .collect(Collectors.toList());
            return flattened;
        } catch (Exception e) {
            throw new PebbleException(e, "An error occurred while flattening the list.", lineNumber, self.getName());
        }
    }
}
