package io.kestra.core.runners.pebble.functions;

import com.mitchellbosecke.pebble.extension.Function;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.kestra.core.runners.pebble.AbstractDate;

import java.time.ZonedDateTime;
import java.util.Map;

public class NowFunction extends AbstractDate implements Function {
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return format(ZonedDateTime.now(), args, context);
    }
}
