package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Test-only deprecated Pebble function used to verify that deprecation metadata
 * is correctly surfaced by {@link io.kestra.core.runners.pebble.PebbleExpressionService}.
 */
@Deprecated
public class DeprecatedTestFunction implements KestraFunction {
    public static final String NAME = "deprecatedFunction";

    @Override
    public List<String> getArgumentNames() {
        return List.of();
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of();
    }

    @Override
    public String replacement() {
        return "replacementFunction";
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        throw new UnsupportedOperationException("Test-only function, not meant to be executed.");
    }
}
