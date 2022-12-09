package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;
import java.util.Map;

public class NumberFilter implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return List.of("type");
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!(input instanceof String)) {
            throw new PebbleException(null, "'number' filter can only be applied to String. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());
        }

        String type = (String) args.getOrDefault("type", "");
        String val = (String) input;

        switch (type) {
            case "INT":
                return NumberUtils.createInteger(val);
            case "FLOAT":
                return NumberUtils.createFloat(val);
            case "LONG":
                return NumberUtils.createLong(val);
            case "DOUBLE":
                return NumberUtils.createDouble(val);
            case "BIGDECIMAL":
                return NumberUtils.createBigDecimal(val);
            case "BIGINTEGER":
                return NumberUtils.createBigInteger(val);
        }

        return NumberUtils.createNumber(val);
    }
}
