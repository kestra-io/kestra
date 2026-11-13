package io.kestra.core.runners.pebble.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class SubstringAfterLastFilter implements Filter {
    private final List<String> argumentNames = new ArrayList<>();

    public SubstringAfterLastFilter() {
        this.argumentNames.add("separator");
    }

    @Override
    public List<String> getArgumentNames() {
        return this.argumentNames;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!args.containsKey("separator")) {
            throw new PebbleException(
                null,
                "The 'substringAfterLast' filter expects an argument 'separator'.",
                lineNumber,
                self.getName()
            );
        }

        Object separatorArgument = args.get("separator");
        if (!(separatorArgument instanceof String separator)) {
            String actualType = separatorArgument == null ? "null" : separatorArgument.getClass().getName();
            throw new PebbleException(
                null,
                "The 'substringAfterLast' filter argument 'separator' must be a string. Actual type was: %s.".formatted(actualType),
                lineNumber,
                self.getName()
            );
        }

        return StringUtils.substringAfterLast(input.toString(), separator);
    }
}
