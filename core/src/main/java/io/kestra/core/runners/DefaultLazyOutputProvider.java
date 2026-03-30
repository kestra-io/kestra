package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.TaskOutputService;

import java.util.*;

/**
 * Default {@link LazyOutputProvider} that wraps a {@link TaskOutputService} and an {@link Execution}.
 * Used on the executor side and in standalone mode where direct DB access is available.
 */
public class DefaultLazyOutputProvider implements LazyOutputProvider {
    private final TaskOutputService taskOutputService;
    private final Execution execution;
    private final Map<String, List<String>> valueToTaskIds;

    public DefaultLazyOutputProvider(TaskOutputService taskOutputService, Execution execution) {
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.execution = execution;
        if (execution != null && execution.getTaskRunList() != null) {
            Map<String, List<String>> vmap = new HashMap<>();
            for (TaskRun tr : execution.getTaskRunList()) {
                if (tr.getValue() != null) {
                    vmap.computeIfAbsent(tr.getValue(), k -> new ArrayList<>()).add(tr.getTaskId());
                }
            }
            this.valueToTaskIds = Map.copyOf(vmap);
        } else {
            this.valueToTaskIds = Collections.emptyMap();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> computeOutputs() {
        return taskOutputService.computeOutputs(execution);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> computeOutputsForTask(String taskId) {
        return taskOutputService.computeOutputsForTask(execution, taskId);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> findTaskIdsWithOutput() {
        return taskOutputService.findTaskIdWithOutputByExecution(execution);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, List<String>> valueToTaskIds() {
        return valueToTaskIds;
    }
}
