package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

@Singleton
public class ValueFromIterationFunction implements Function {
    // Arg names: index, taskId, values (optional)
    @Override
    public List<String> getArgumentNames() {
        return List.of("index", "taskId", "values");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("index") || !args.containsKey("taskId")) {
            throw new PebbleException(null, "The 'valueFromIteration' function expects at least 'index' and 'taskId' arguments.", lineNumber, self.getName());
        }

        Object indexObj = args.get("index");
        Object taskIdObj = args.get("taskId");

        // Pebble numeric types might be Long/Double, so handle Number
        if (!(indexObj instanceof Number)) {
            throw new PebbleException(null, "The 'valueFromIteration' function expects a numeric 'index' argument.", lineNumber, self.getName());
        }
        int index = ((Number) indexObj).intValue();

        if (!(taskIdObj instanceof String taskId)) {
            throw new PebbleException(null, "The 'valueFromIteration' function expects a string 'taskId' argument.", lineNumber, self.getName());
        }

        if (index < 0) {
            return null;
        }

        // Fetch the global outputs (from the template context)
        Map<String, Object> outputs = (Map<String, Object>) context.getVariable("outputs");
        if (outputs == null) {
            return null;
        }

        Object taskOutputs = outputs.get(taskId);
        if (taskOutputs == null) {
            return null;
        }

        // Optional explicit values list (if passed)
        Object valuesArg = args.get("values");

        // If values provided and is a List, use it to resolve the key (recommended when you can)
        if (valuesArg instanceof List<?> valuesList) {
            if (index >= valuesList.size()) {
                return null;
            }
            Object key = valuesList.get(index);
            if (taskOutputs instanceof Map<?, ?> m) {
                return m.get(key);
            }
            // if taskOutputs is a list, return element by index if applicable
            if (taskOutputs instanceof List<?> l) {
                if (index >= l.size()) {
                    return null;
                }
                return l.get(index);
            }
            return null;
        }

        // If the taskOutputs is a Map, we iterate entries in insertion order and pick the index-th value
        if (taskOutputs instanceof Map<?, ?> taskOutputMap) {
            // iterate to the index-th entry value
            int i = 0;
            for (Object key : taskOutputMap.keySet()) {
                if (i == index) {
                    return taskOutputMap.get(key);
                }
                i++;
            }
            return null; // out of range
        }

        // If it's a list (rare), pick index
        if (taskOutputs instanceof List<?> taskOutputList) {
            if (index >= taskOutputList.size()) {
                return null;
            }
            return taskOutputList.get(index);
        }

        return null;
    }
}
