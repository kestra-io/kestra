package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.runners.RunVariables;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
public class EnvFunction implements KestraFunction {
    public static final String NAME = "env";

    private static final String NAME_ARG = "name";
    private static final String DEFAULT_ARG = "default";

    @Override
    public List<String> getArgumentNames() {
        return List.of(NAME_ARG, DEFAULT_ARG);
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put(NAME_ARG, "'ENV_NAME'");
        defaults.put(DEFAULT_ARG, null);
        return defaults;
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String name = getName(args, self, lineNumber);
        Object defaultValue = args.get(DEFAULT_ARG);
        Object envsVariable = context.getVariable(RunVariables.ENVS);

        if (!(envsVariable instanceof Map<?, ?> envs)) {
            return defaultValue;
        }

        Object value = envs.get(name);
        if (value == null || value.toString().isEmpty()) {
            return defaultValue;
        }

        return value;
    }

    protected String getName(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey(NAME_ARG)) {
            throw new PebbleException(null, "The 'env' function expects an argument 'name'.", lineNumber, self.getName());
        }

        return (String) args.get(NAME_ARG);
    }
}
