package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class UUIDFunction implements KestraFunction {
    public static final String NAME = "uuid";

    private static final TimeBasedEpochRandomGenerator generator = Generators.timeBasedEpochRandomGenerator();

    @Override
    public Object execute(
        Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return generator.generate().toString();
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
