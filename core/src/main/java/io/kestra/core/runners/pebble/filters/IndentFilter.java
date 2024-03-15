package io.kestra.core.runners.pebble.filters;

import io.kestra.core.runners.pebble.AbstractIndent;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Map;

public class IndentFilter extends AbstractIndent implements Filter {
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }
        if (input.toString().isEmpty()) {
            return input.toString();
        }

        if (!args.containsKey("amount")) {
            throw new PebbleException(null, "The 'indent' filter expects an integer as argument 'amount'.", lineNumber, self.getName());
        }

        int amount = ((Long) args.get("amount")).intValue();
        if (!(amount >= 0)) {
            throw new PebbleException(null, "The 'indent' filter expects a positive integer >=0 as argument 'amount'.", lineNumber, self.getName());
        }

        String prefix = prefix(args);
        String newLine = getLineSeparator(input.toString());

        // indent filter adds N amount of spaces to each line except for the first one (assuming the first line is already indented in place)
        return input.toString().replace(newLine, newLine + prefix.repeat(amount));
    }
}
