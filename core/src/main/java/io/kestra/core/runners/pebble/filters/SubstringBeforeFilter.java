package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SubstringBeforeFilter implements Filter {
    private final List<String> argumentNames = new ArrayList<>();

    public SubstringBeforeFilter() {
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
                "The 'substringBefore' filter expects an argument 'separator'.",
                lineNumber,
                self.getName()
            );
        }

        String separator = (String) args.get("separator");;

        return StringUtils.substringBefore(input.toString(), separator);
    }
}
