package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TasksWithStateFunction implements Function {
    public List<String> getArgumentNames() {
        return List.of("state");
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
            globalTasksMap.forEach((taskId, taskDetailsObj) -> {
                if (taskDetailsObj instanceof Map) {
                    Map<String, Object> taskDetailsMap = (Map<String, Object>) taskDetailsObj;
                    taskDetailsMap.forEach((key, valueObj) -> {
                        Map<String, Object> transformedTask = new HashMap<>();
                        transformedTask.put("taskId", taskId);

                        if ("state".equals(key)) {
                            if (valueObj instanceof String) {
                                String state = ((String) valueObj).toUpperCase();
                                if (stateToFilter.equals(state)) {
                                    transformedTask.put("state", state);
                                    filteredTasks.add(transformedTask);
                                }

                            }
                            else if (valueObj instanceof Map) {
                                Map<String, Object> nestedMap = (Map<String, Object>) valueObj;
                                Object stateFromNested = nestedMap.get("state");
                                if (stateFromNested instanceof String) {
                                    String state = ((String) stateFromNested).toUpperCase();
                                    if (stateToFilter.equals(state)) {
                                        transformedTask.put("state", state);
                                        transformedTask.put("value", "state");
                                        filteredTasks.add(transformedTask);
                                    }
                                }
                            }
                        } else if (valueObj instanceof Map) {
                            Map<String, Object> nestedMap = (Map<String, Object>) valueObj;
                            Object stateFromNested = nestedMap.get("state");
                            if (stateFromNested instanceof String) {
                                String state = ((String) stateFromNested).toUpperCase();
                                if (stateToFilter.equals(state)) {
                                    transformedTask.put("state", state);
                                    transformedTask.put("value", key);
                                    filteredTasks.add(transformedTask);
                                }
                            }
                        }
                    });
                }
            });
        }

        return filteredTasks;
    }
}
