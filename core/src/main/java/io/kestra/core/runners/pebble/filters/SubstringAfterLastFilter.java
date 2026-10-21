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

        Object separator = args.get("separator");
        if (separator == null) {
            throw new PebbleException(
                null,
                "The 'substringAfterLast' filter expects an argument 'separator'.",
                lineNumber,
                self.getName()
            );
        }

        return StringUtils.substringAfterLast(input.toString(), separator.toString());
    }
}
