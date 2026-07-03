package io.kestra.core.runners.pebble.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class TasksWithStateFunction implements KestraFunction {
    public static final String NAME = "tasksWithState";
    public List<String> getArgumentNames() {
        return List.of("state");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of("state", "'FAILED'");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("state")) {
            throw new PebbleException(null, "The 'tasksWithState' function expects an argument 'state'.", lineNumber, self.getName());
        }
        String stateToFilter = ((String) args.get("state")).toUpperCase();

        EvaluationContextImpl evaluationContext = (EvaluationContextImpl) context;

        Map<String, Object> globalTasksMap = (Map<String, Object>) evaluationContext.getScopeChain().getGlobalScopes().stream()
            .flatMap(scope -> scope.getKeys().stream())
            .distinct()
            .filter(key -> "tasks".equals(key)) // Filter for the "tasks" key specifically
            .collect(HashMap::new, (m, k) -> m.put(k, context.getVariable(k)), HashMap::putAll)
            .get("tasks");

        List<Map<String, Object>> filteredTasks = new ArrayList<>();

        if (globalTasksMap != null) {
            globalTasksMap.forEach((taskId, taskDetailsObj) ->
            {
                if (taskDetailsObj instanceof Map) {
                    Map<String, Object> taskDetailsMap = (Map<String, Object>) taskDetailsObj;
                    taskDetailsMap.forEach((key, valueObj) ->
                    {
                        Map<String, Object> transformedTask = new HashMap<>();
                        transformedTask.put("taskId", taskId);

                        if ("state".equals(key)) {
                            if (valueObj instanceof Map) {
                                Map<String, Object> nestedMap = (Map<String, Object>) valueObj;
                                String state = toState(nestedMap.get("state"));
                                if (stateToFilter.equals(state)) {
                                    transformedTask.put("state", state);
                                    transformedTask.put("value", "state");
                                    filteredTasks.add(transformedTask);
                                }
                            } else {
                                String state = toState(valueObj);
                                if (stateToFilter.equals(state)) {
                                    transformedTask.put("state", state);
                                    filteredTasks.add(transformedTask);
                                }
                            }
                        } else if (valueObj instanceof Map) {
                            Map<String, Object> nestedMap = (Map<String, Object>) valueObj;
                            String state = toState(nestedMap.get("state"));
                            if (stateToFilter.equals(state)) {
                                transformedTask.put("state", state);
                                transformedTask.put("value", key);
                                filteredTasks.add(transformedTask);
                            }
                        }
                    });
                }
            });
        }

        return filteredTasks;
    }

    /**
     * Normalizes a task state value to its uppercase name.
     * The {@code tasks} variable holds the state as a {@link io.kestra.core.models.flows.State.Type}
     * enum when rendered executor-side (e.g. {@code runIf}) and as a {@link String} when rendered
     * worker-side after serialization; both must be matched. Returns {@code null} for unsupported values.
     */
    private static String toState(Object value) {
        if (value instanceof String || value instanceof Enum) {
            return value.toString().toUpperCase();
        }
        return null;
    }
}
