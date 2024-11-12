package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CurrentEachOutputFunction implements Function {

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
        List<Map<?, ?>> parents = (List<Map<?, ?>>) context.getVariable("parents");
        if (parents != null && !parents.isEmpty()) {
            Collections.reverse(parents);
            for (Map<?, ?> parent : parents) {
                Map<?, ?> taskrun = (Map<?, ?>) parent.get("taskrun");
                if (taskrun != null) {
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
}
