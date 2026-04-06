package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.kestra.core.utils.IdUtils;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class IDFunction implements KestraFunction {
    public static final String NAME = "id";
    @Override
    public Object execute(
        Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return IdUtils.create();
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
