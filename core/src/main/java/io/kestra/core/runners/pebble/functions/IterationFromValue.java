package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IterationValueFunction implements Function {

    @Override
    public List<String> getArgumentNames() {
        return List.of("iteration");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("iteration")) {
            throw new PebbleException(null, "The 'iterationValue' function expects an argument 'iteration'.", lineNumber, self.getName());
        }

        int iteration = ((Number) args.get("iteration")).intValue();
        if (iteration < 0) {
            return null;
        }

        Map<String, Object> outputs = (Map<String, Object>) context.getVariable("outputs");
        if (outputs == null) {
            return null;
        }

        List<Map<String, Object>> parents = (List<Map<String, Object>>) context.getVariable("parents");
        if (parents != null && !parents.isEmpty()) {
            Collections.reverse(parents); // closest parent first
            for (Map<String, Object> parent : parents) {
                Map<String, Object> taskrun = (Map<String, Object>) parent.get("taskrun");
                if (taskrun != null) {
                    Object value = outputs.get(taskrun.get("value"));
                    if (value instanceof List<?> list && iteration < list.size()) {
                        return list.get(iteration);
                    }
                    outputs = (Map<String, Object>) value; // drill down
                }
            }
        }

        // fallback: top-level outputs
        for (Object output : outputs.values()) {
            if (output instanceof List<?> list && iteration < list.size()) {
                return list.get(iteration);
            }
        }

        return null;
    }
}
