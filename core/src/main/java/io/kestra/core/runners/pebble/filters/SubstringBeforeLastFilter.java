package io.kestra.core.runners.pebble.filters;

import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SubstringBeforeLastFilter implements Filter {
    private final List<String> argumentNames = new ArrayList<>();

    public SubstringBeforeLastFilter() {
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
                "The 'substringBeforeLast' filter expects an argument 'separator'.",
                lineNumber,
                self.getName()
            );
        }

        String separator = (String) args.get("separator");;

        return StringUtils.substringBeforeLast(input.toString(), separator);
    }
}
