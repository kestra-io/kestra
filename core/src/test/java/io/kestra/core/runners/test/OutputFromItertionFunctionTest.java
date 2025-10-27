package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pebble function to retrieve outputs from a specific iteration in a ForEach loop.
 * 
 * This function enables accessing previous (or any) iteration outputs by iteration index.
 * Useful for scenarios like cumulative calculations, comparisons between iterations,
 * or conditional processing based on previous results.
 * 
 * Usage: {{ outputFromIteration(outputs.taskId, iterationIndex) }}
 * 
 * Example:
 * <pre>
 * id: each_example
 * namespace: company.team
 * tasks:
 *   - id: 1_each
 *     type: io.kestra.plugin.core.flow.ForEach
 *     values: '[10, 20, 30, 40, 50]'
 *     tasks:
 *       - id: cumulative_sum
 *         type: io.kestra.plugin.core.debug.Return
 *         format: |
 *           {% if taskrun.iteration == 0 %}{{ taskrun.value }}
 *           {% else %}{{ taskrun.value + outputFromIteration(outputs.1_each, taskrun.iteration - 1).value | int }}
 *           {% endif %}
 * </pre>
 * 
 * Note: Currently optimized for flat ForEach loops. Nested ForEach support may require
 * additional context handling and will be added in future iterations.
 */
public class OutputFromIterationFunction implements Function {

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("outputs")) {
            throw new PebbleException(null, "The 'outputFromIteration' function expects an argument 'outputs'.", lineNumber, self.getName());
        }

        if (!args.containsKey("iteration")) {
            throw new PebbleException(null, "The 'outputFromIteration' function expects an argument 'iteration'.", lineNumber, self.getName());
        }

        Object outputsArg = args.get("outputs");
        if (!(outputsArg instanceof Map) && !(outputsArg instanceof List)) {
            throw new PebbleException(null, "The 'outputFromIteration' function expects argument 'outputs' to be a Map or List.", lineNumber, self.getName());
        }

        Object iterationArg = args.get("iteration");
        int iteration;
        
        try {
            if (iterationArg instanceof Number) {
                iteration = ((Number) iterationArg).intValue();
            } else if (iterationArg instanceof String) {
                iteration = Integer.parseInt((String) iterationArg);
            } else {
                throw new PebbleException(null, "The 'outputFromIteration' function expects argument 'iteration' to be a Number or String.", lineNumber, self.getName());
            }
        } catch (NumberFormatException e) {
            throw new PebbleException(null, "The 'outputFromIteration' function expects argument 'iteration' to be a valid integer.", lineNumber, self.getName());
        }

        if (outputsArg instanceof List) {
            List<?> outputsList = (List<?>) outputsArg;
            
            if (iteration < 0 || iteration >= outputsList.size()) {
                throw new PebbleException(null, 
                    String.format("Iteration index %d is out of bounds. Available iterations: 0 to %d", iteration, outputsList.size() - 1), 
                    lineNumber, self.getName());
            }
            
            return outputsList.get(iteration);
        } else {
            Map<?, ?> outputs = (Map<?, ?>) outputsArg;
            
            if (iteration < 0) {
                throw new PebbleException(null, 
                    String.format("Iteration index %d is negative. Iteration index must be >= 0", iteration), 
                    lineNumber, self.getName());
            }
            
            List<Map<?, ?>> parents = (List<Map<?, ?>>) context.getVariable("parents");
            if (parents != null && !parents.isEmpty()) {
                throw new PebbleException(null, 
                    "The 'outputFromIteration' function does not currently support nested ForEach loops. " +
                    "This feature is planned for a future release. " +
                    "Please use this function only in flat (non-nested) ForEach contexts.", 
                    lineNumber, self.getName());
            }
            
            String iterationKey = String.valueOf(iteration);
            
            if (!outputs.containsKey(iterationKey)) {
                throw new PebbleException(null, 
                    String.format("Iteration index %d not found in outputs. Available keys: %s", iteration, outputs.keySet()), 
                    lineNumber, self.getName());
            }
            
            return outputs.get(iterationKey);
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("outputs", "iteration");
    }
}
