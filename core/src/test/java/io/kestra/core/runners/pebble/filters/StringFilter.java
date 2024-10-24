package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class StringFilter implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return null; // No arguments needed for this filter
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context,
            int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        // Handle numeric types (Integer, Long, Float, Double, BigDecimal, BigInteger)
        if (input instanceof Number) {
            return input.toString(); // Convert numeric input to string
        }

        // Handle string inputs that represent numbers (parse them as numbers and return
        // their string form)
        if (input instanceof String) {
            try {
                return new BigDecimal((String) input).toString(); // Convert the string representation of a number to
                                                                  // string form
            } catch (NumberFormatException e) {
                throw new PebbleException(null,
                        "'string' filter expects a valid number. Received an invalid numeric string: " + input,
                        lineNumber, self.getName());
            }
        }

        // If the input is not a number or numeric string, throw an exception
        throw new PebbleException(null,
                "'string' filter expects a Number (INT, FLOAT, DOUBLE, etc.) or numeric String. Received: "
                        + input.getClass().getName(),
                lineNumber, self.getName());
    }
}
