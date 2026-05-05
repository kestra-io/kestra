package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class CurrentEachOutputFunction implements KestraFunction {
    public static final String NAME = "currentEachOutput";

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("outputs")) {
            throw new PebbleException(null, "The 'currentEachOutput' function expects an argument 'outputs'.", lineNumber, self.getName());
        }

        if (!(args.get("outputs") instanceof Map)) {
            throw new PebbleException(null, "The 'currentEachOutput' function expects an argument 'outputs' with type map.", lineNumber, self.getName());
        }

        Map<?, ?> outputs = (Map<?, ?>) args.get("outputs");
        List<Map<?, ?>> parents = ((List<Map<?, ?>>) context.getVariable("parents")).reversed();
        if (parents != null && !parents.isEmpty()) {
            for (Map<?, ?> parent : parents) {
                Map<?, ?> taskrun = (Map<?, ?>) parent.get("taskrun");
                if (taskrun != null) {
                    if (outputs.get(taskrun.get("value")) == null) {
                        return null;
                    }
                    outputs = (Map<?, ?>) outputs.get(taskrun.get("value"));
                }
            }
        }
        Map<?, ?> taskrun = (Map<?, ?>) context.getVariable("taskrun");

        return outputs.get(taskrun.get("value"));
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("outputs");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of("outputs", "outputs.forEach");
    }
}
