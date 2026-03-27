package io.kestra.core.runners.pebble.functions;

import java.util.Map;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Deprecated
public class JsonFunction extends FromJsonFunction {
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return super.execute(args, self, context, lineNumber);
    }
}
