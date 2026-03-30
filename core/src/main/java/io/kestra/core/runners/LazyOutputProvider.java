package io.kestra.core.runners;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstraction for lazy output resolution used by {@link LazyOutputsMap}.
 * <p>
 * On executor/standalone: delegates to {@link io.kestra.core.services.TaskOutputService} with an Execution.
 * On distributed workers: delegates via gRPC to the controller.
 */
public interface LazyOutputProvider {

    /**
     * Compute all outputs for the execution (fallback for full load).
     */
    Map<String, Object> computeOutputs();

    /**
     * Compute outputs for a specific task within the execution.
     *
     * @param taskId the task ID to compute outputs for
     */
    Map<String, Object> computeOutputsForTask(String taskId);

    /**
     * Find task IDs that have outputs for the execution.
     */
    Set<String> findTaskIdsWithOutput();

    /**
     * Get the mapping from task run values to task IDs, used for iteration support.
     */
    Map<String, List<String>> valueToTaskIds();
}
