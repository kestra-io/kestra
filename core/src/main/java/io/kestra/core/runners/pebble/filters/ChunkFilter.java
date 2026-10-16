package io.kestra.core.runners.pebble.filters;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class ChunkFilter implements Filter {
    private static final String INVALID_SIZE_MESSAGE = "'chunk' filter argument 'size' must be an integer between 1 and %d, inclusive.".formatted(Integer.MAX_VALUE);

    @Override
    public List<String> getArgumentNames() {
        return List.of("size");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!args.containsKey("size")) {
            throw new PebbleException(null, "'chunk' filter expects an argument 'size'.", lineNumber, self.getName());
        }

        if (!(input instanceof List)) {
            throw new PebbleException(null, "'chunk' filter can only be applied to List. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());
        }

        Object sizeObj = args.get("size");
        if (!(sizeObj instanceof Number)) {
            throw new PebbleException(null, "'chunk' filter argument 'size' must be a number. Actual type was: " + sizeObj.getClass().getName(), lineNumber, self.getName());
        }

        int size;
        try {
            size = new BigDecimal(sizeObj.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new PebbleException(exception, INVALID_SIZE_MESSAGE, lineNumber, self.getName());
        }

        if (size <= 0) {
            throw new PebbleException(null, INVALID_SIZE_MESSAGE, lineNumber, self.getName());
        }

        return Lists.partition((List) input, size);
    }
}
