package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Deprecated
public class JsonFunction extends FromJsonFunction {

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        log.warn("The 'json' function is deprecated, please use 'fromJson' instead");

        return super.execute(args, self, context, lineNumber);
    }
}
