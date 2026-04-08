package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.kestra.core.runners.pebble.DeprecatedPebble;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

@Deprecated
@DeprecatedPebble(replaceWith = "testReplacement")
public class TestDeprecatedFunction implements KestraFunction {
    public static final String NAME = "testDeprecated";

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return "test";
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of();
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of();
    }
}
