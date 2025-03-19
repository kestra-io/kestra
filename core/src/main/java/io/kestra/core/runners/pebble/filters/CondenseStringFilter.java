package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class CondenseStringFilter implements Filter {

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input instanceof String str) {
            return str.replace("\n", "");
        }

        throw new PebbleException(null, "condense can only be applied on strings", lineNumber, self.getName());
    }

    @Override
    public List<String> getArgumentNames() {
        return null;
    }
}
