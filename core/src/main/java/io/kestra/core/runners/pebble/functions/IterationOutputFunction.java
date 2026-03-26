package io.kestra.core.runners.pebble.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class IterationOutputFunction implements Function {
    public static final String NAME = "iterationOutput";

    private static final String TASK_ID_ARG = "taskId";

    private static final String ITERATION_ARG = "iteration";

    @Override
    public List<String> getArgumentNames() {
        return List.of(TASK_ID_ARG, ITERATION_ARG);
    }

    @Override
    public Object execute(Map<String, Object> args,
        PebbleTemplate self,
        EvaluationContext context,
        int lineNumber) {

        Object taskIdObj = args.get("taskId");
        Object iterationObj = args.get("iteration");

        Map<?, ?> currentTaskRun = (Map<?, ?>) context.getVariable("taskrun");
        if (!currentTaskRun.containsKey("iteration")) {
            throw new PebbleException(null, " 'iterationOutputs()' function should be used inside iterative tasks only", lineNumber, self.getName());
        }

        String taskId;
        if (taskIdObj == null) {
            // when no taskId is provided, the default taskId is the current task
            Map<?, ?> taskMetaData = (Map<?, ?>) context.getVariable("task");
            taskId = (String) taskMetaData.get("id");
        } else {
            taskId = (String) taskIdObj;
        }

        int iteration;
        if (iterationObj == null) {
            // when no iteration is provided, the default iteration is the previous iteration
            iteration = (Integer) currentTaskRun.get("iteration") - 1;
        } else {
            try {
                iteration = Integer.parseInt(iterationObj.toString());
            } catch (NumberFormatException e) {
                throw new PebbleException(e, "The 'iteration' argument for 'iterationOutputs' must be an integer, but got: " + iterationObj, lineNumber, self.getName());
            }
        }

        if (iteration < 0) {
            throw new PebbleException(null, "Cannot fetch iteration " + iteration + ": no previous iteration exists.", lineNumber, self.getName());
        }

        Map<?, ?> outputs = (Map<?, ?>) context.getVariable("outputs");

        if (outputs.get(taskId) == null) {
            return null;
        }
        Map<?, ?> targetOutputs = (Map<?, ?>) outputs.get(taskId);

        List<Map<?, ?>> parents = ((List<Map<?, ?>>) context.getVariable("parents")).reversed();
        if (parents != null && !parents.isEmpty()) {
            for (Map<?, ?> parent : parents) {
                Map<?, ?> taskrun = (Map<?, ?>) parent.get("taskrun");
                if (taskrun != null) {
                    if (targetOutputs.get(taskrun.get("value")) == null) {
                        return null;
                    }
                    targetOutputs = (Map<?, ?>) targetOutputs.get(taskrun.get("value"));
                }
            }
        }
        if (iteration >= targetOutputs.size()) {
            throw new PebbleException(
                null,
                "The provided index (" + iteration + ") is out of range. "
                    + "It refers to an iteration whose outputs do not exist yet. "
                    + "Maximum valid index is " + (targetOutputs.size() - 1) + ".",
                lineNumber, self.getName()
            );
        }
        List<?> taskValues = new ArrayList<>(targetOutputs.keySet());

        Object targetValue = taskValues.get(iteration);
        Map<?, ?> finalOutput = (Map<?, ?>) targetOutputs.get(targetValue);

        return finalOutput.get("value");
    }
}
