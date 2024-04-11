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
        return abstractApply(input, args, self, context, lineNumber, "indent");
    }
}
