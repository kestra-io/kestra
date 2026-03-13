package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IterationOutputsFunction implements Function {
    public static final String NAME = "iterationOutputs";

    private static final String ITERATION_ARG = "iteration";

    private static final String TASK_ID_ARG = "taskId";

    @Override
    public List<String> getArgumentNames() {
        return List.of(ITERATION_ARG, TASK_ID_ARG);
    }

    @Override
    public Object execute(Map<String, Object> args,
                          PebbleTemplate self,
                          EvaluationContext context,
                          int lineNumber) {

        Object iterationObj = args.get("iteration");
        Object taskIdObj = args.get("taskId");

        if (iterationObj == null) {
            throw new PebbleException(null, "The 'iterationOutputs' function requires an argument named 'iteration'.", lineNumber, self.getName());
        }

        String taskId;
        // when no taskId provided the default is the current task
        if (taskIdObj == null) {
            Map<?, ?> taskMetaData = (Map<?, ?>) context.getVariable("task");
            taskId = (String) taskMetaData.get("id");
        }
        else {
            taskId = (String) taskIdObj;
        }

        int iteration;
        try {
            iteration = Integer.parseInt(iterationObj.toString());
        } catch (NumberFormatException e) {
            throw new PebbleException(e, "The 'iteration' argument for 'iterationOutputs' must be an integer, but got: " + iterationObj, lineNumber, self.getName());
        }

        if (iteration < 0) {
            throw new PebbleException(null, "Cannot fetch iteration " + iteration + ": no previous iteration exists.", lineNumber, self.getName());
        }

        Map<?, ?> outputs = (Map<?, ?>) context.getVariable("outputs");

        Map<?,?> targetOutputs = (Map<?, ?>) outputs.get(taskId);

        List<Map<?, ?>> parents = (List<Map<?, ?>>) context.getVariable("parents");
        if (parents != null && !parents.isEmpty()) {
            Collections.reverse(parents);
            for (Map<?, ?> parent : parents) {
                Map<?, ?> taskrun = (Map<?, ?>) parent.get("taskrun");
                if (taskrun != null) {
                    if(targetOutputs.get(taskrun.get("value")) != null){
                        targetOutputs = (Map<?, ?>) targetOutputs.get(taskrun.get("value"));
                    }
                    else{
                        throw new PebbleException(null, "The provided task with taskId = " + taskId + " has no execution outputs for the current 'ForEach' path", lineNumber, self.getName());
                    }
                }
            }

        }
        //  output of current or future iterations can't occur in current iteration
        if (iteration == targetOutputs.size()) {
            throw new PebbleException(null,
                    "The provided index (" + iteration + ") refers to the current iteration, "
                            + "whose outputs are not yet available at runtime.",
                    lineNumber, self.getName());
        }
        if (iteration > targetOutputs.size()) {
            throw new PebbleException(null,
                    "The provided index (" + iteration + ") is out of range. "
                            + "It refers to a future iteration whose outputs do not exist yet. "
                            + "Maximum valid index is " + (targetOutputs.size() - 1) + ".",
                    lineNumber, self.getName());
        }
        List<?> taskValues = new ArrayList<>(targetOutputs.keySet());

        Object targetValue = taskValues.get(iteration);
        Map<?, ?> finalOutput = (Map<?, ?>) targetOutputs.get(targetValue);

        return finalOutput.get("value");
    }
}
