package io.kestra.core.models.tasks;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.WorkerJobLifecycle;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphTask;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.runners.RunContext;

import java.util.List;

/**
 * Interface for tasks that are run in the Worker.
 */
public interface RunnableTask <T extends Output> extends Plugin, WorkerJobLifecycle {
    /**
     * This method is called inside the Worker to run (execute) the task.
     */
    T run(RunContext runContext) throws Exception;

    /**
     * Create the topology representation of a runnable task.
     * <p>
     * By default, it returns a single GraphTask, tasks may override it to provide a custom topology representation.
     */
    default AbstractGraph graph(TaskRun taskRun, List<String> values, RelationType relationType) {
        return new GraphTask((Task) this, taskRun, values, relationType);
    }
}
