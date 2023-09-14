package io.kestra.core.runners.pebble.filters;


import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class UrlDecode implements Filter {

    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (input instanceof String) {
            return URLDecoder.decode((String) input, StandardCharsets.UTF_8);
        }

        throw new PebbleException(null, "'urldecode' filter can only be applied to String. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());
    }

}
