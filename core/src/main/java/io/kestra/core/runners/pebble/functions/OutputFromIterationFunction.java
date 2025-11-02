package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OutputFromIterationFunction implements Function {
    public static final String NAME = "outputFromIteration";

    private static final String INDEX_ARG = "index";

    @Override
    public List<String> getArgumentNames() {
        return List.of(INDEX_ARG);
    }

    @Override
    public Object execute(Map<String, Object> args,
                          PebbleTemplate self,
                          EvaluationContext context,
                          int lineNumber) {

        Object indexObj = args.get("index");
        if (indexObj == null) {
            throw new PebbleException(null, "The 'outputFromIteration' function requires an argument named 'index'.", lineNumber, self.getName());
        }

        int index;
        try {
            index = Integer.parseInt(indexObj.toString());
        } catch (NumberFormatException e) {
            throw new PebbleException(e, "The 'index' argument for 'outputFromIteration' must be an integer, but got: " + indexObj, lineNumber, self.getName());
        }

        if (index < 0) {
            throw new PebbleException(null, "Cannot fetch iteration " + index + ": no previous iteration exists.", lineNumber, self.getName());
        }

        List<?> parents = (List<?>) context.getVariable("parents");
        if (parents != null && !parents.isEmpty()) {
            throw new PebbleException(null, "The 'outputFromIteration' function is not supported inside nested loops. ", lineNumber, self.getName()
            );
        }


        Map<?, ?> taskMetaData = (Map<?, ?>) context.getVariable("task");
        String taskId = (String) taskMetaData.get("id");

        Map<?, ?> outputs = (Map<?, ?>) context.getVariable("outputs");
        Map<?, ?> taskOutputs = (Map<?, ?>) outputs.get(taskId);

        //  output of current or future iterations can't occur in current iteration
        if (index == taskOutputs.size()) {
            throw new PebbleException(null, "The provided index (" + index + ") refers to the current iteration, " + "whose outputs are not yet available at runtime.", lineNumber, self.getName());
        }
        if (index > taskOutputs.size()) {
            throw new PebbleException(null, "The provided index (" + index + ") is out of range. " + "It refers to a future iteration whose outputs do not exist yet. " + "Maximum valid index is " + (taskOutputs.size() - 1) + ".", lineNumber, self.getName());
        }

       List<?>taskValues= new ArrayList<>(taskOutputs.keySet());

        Object targetValue = taskValues.get(index);
        Map<?, ?> finalOutput = (Map<?, ?>) taskOutputs.get(targetValue);

        return finalOutput.get("value");
    }
}
