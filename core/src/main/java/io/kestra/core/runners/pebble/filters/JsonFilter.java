package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Deprecated
public class JsonFilter extends JsonEncodeFilter {

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        log.warn("The 'json' filter is deprecated, please use 'jsonEncode' instead");

        return super.apply(input, args, self, context, lineNumber);
    }
}
