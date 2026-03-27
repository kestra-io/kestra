package io.kestra.core.runners.pebble.filters;

import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Deprecated
public class JsonFilter extends ToJsonFilter {
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        return super.apply(input, args, self, context, lineNumber);
    }
}
