package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.utils.MapUtils;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Retrieves the outputs of a parent.
 * By default, it retrieves the output of the direct parent.
 * If an index is passed to the function, it retrieves the outputs of this specific parent (start at 0)
 */
public class ParentOutputFunction implements KestraFunction {
    public static final String NAME = "parentOutput";
    @Override
    public List<String> getArgumentNames() {
        return List.of("index");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("index", null);
        return defaults;
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (args.containsKey("index")) {
            if (args.get("index") instanceof Long index) {
                List<Map<?, ?>> parents = (List<Map<?, ?>>) context.getVariable("parents");
                if (index + 1 > parents.size()) {
                    throw new PebbleException(null, "Invalid index: " + index + " for parents size: " + parents.size(), lineNumber, self.getName());
                }
                return retrieveOutput(context, parents.reversed().get(index.intValue()));
            } else {
                throw new PebbleException(null, "The 'parentOutput' function expects an argument 'index' of type integer.", lineNumber, self.getName());
            }
        }
        Map<?, ?> parent = (Map<?, ?>) context.getVariable("parent");
        return retrieveOutput(context, parent);
    }

    private Object retrieveOutput(EvaluationContext context, Map<?, ?> parent) {
        String id = (String) ((Map<?, ?>) parent.get("task")).get("id");
        String value = (String) MapUtils.emptyOnNull((Map<?, ?>) parent.get("taskrun")).get("value");
        Map<?, ?> outputs = (Map<?, ?>) context.getVariable("outputs");
        Map<?, ?> parentOutput = (Map<?, ?>) outputs.get(id);
        if (parentOutput != null) {
            return value == null ? parentOutput : parentOutput.get(value);
        }
        return null;
    }

}
