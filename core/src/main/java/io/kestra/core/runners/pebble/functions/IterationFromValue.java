package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ValueFromIterationFunction implements Function {

    @Override
    public List<String> getArgumentNames() {
        return List.of("iteration", "taskId");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("iteration") || !args.containsKey("taskId")) {
            throw new PebbleException(
                null,
                "The 'valueFromIteration' function expects arguments 'iteration' and 'taskId'.",
                lineNumber,
                self.getName()
            );
        }

        int iteration = ((Number) args.get("iteration")).intValue();
        String taskId = (String) args.get("taskId");

        if (iteration < 0) {
            return null; // cannot fetch negative iterations
        }

        // Get all outputs
        Map<String, Object> outputs = (Map<String, Object>) context.getVariable("outputs");
        if (outputs == null || !outputs.containsKey(taskId)) {
            return null;
        }

        Object value = outputs.get(taskId);

        if (value instanceof List<?> list) {
            // ensure iteration exists
            if (iteration >= list.size()) {
                return null;
            }
            return list.get(iteration);
        }

        return null; // fallback: value is not a list
    }
}
