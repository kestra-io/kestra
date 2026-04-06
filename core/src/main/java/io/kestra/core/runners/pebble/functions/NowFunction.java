package io.kestra.core.runners.pebble.functions;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import io.kestra.core.runners.pebble.AbstractDate;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class NowFunction extends AbstractDate implements KestraFunction {
    public static final String NAME = "now";
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return format(ZonedDateTime.now(), args, context);
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("format", null);
        defaults.put("timeZone", null);
        defaults.put("existingFormat", null);
        defaults.put("locale", null);
        return defaults;
    }
}
