package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Extracts a specific output key from all iterations of a Loop task's outputs list. */
public class LoopOutputsFunction implements KestraFunction {
    public static final String NAME = "loopOutputs";

    private static final String ARG_OUTPUTS = "outputs";
    private static final String ARG_NAME = "name";
    private static final String ITERATION_OUTPUTS_KEY = "outputs";

    private static final List<String> ARGUMENT_NAMES = List.of(ARG_OUTPUTS, ARG_NAME);
    private static final Map<String, String> ARGUMENT_DEFAULTS = Map.of(
        ARG_OUTPUTS, "outputs.myLoop.outputs",
        ARG_NAME, "'myOutputName'"
    );

    @Override
    public List<String> getArgumentNames() {
        return ARGUMENT_NAMES;
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return ARGUMENT_DEFAULTS;
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object outputsArg = args.get(ARG_OUTPUTS);
        if (outputsArg == null) {
            throw new PebbleException(null, "The 'loopOutputs' function expects a non-null argument 'outputs'.", lineNumber, self.getName());
        }
        if (!(outputsArg instanceof List<?>)) {
            throw new PebbleException(null, "The 'loopOutputs' function expects 'outputs' to be a list.", lineNumber, self.getName());
        }

        Object nameArg = args.get(ARG_NAME);
        if (nameArg == null) {
            throw new PebbleException(null, "The 'loopOutputs' function expects a non-null argument 'name'.", lineNumber, self.getName());
        }
        if (!(nameArg instanceof String)) {
            throw new PebbleException(null, "The 'loopOutputs' function expects 'name' to be a string.", lineNumber, self.getName());
        }

        String key = (String) nameArg;
        List<?> loopOutputs = (List<?>) outputsArg;
        List<Object> result = new ArrayList<>(loopOutputs.size());

        for (Object loopOutputObj : loopOutputs) {
            if (loopOutputObj instanceof Map<?, ?> loopOutput) {
                Object iterationOutputs = loopOutput.get(ITERATION_OUTPUTS_KEY);
                result.add(iterationOutputs instanceof Map<?, ?> outputs ? outputs.get(key) : null);
            } else {
                result.add(null);
            }
        }

        return result;
    }
}
