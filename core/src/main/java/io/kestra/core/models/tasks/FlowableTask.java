package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

/**
 * Interface for tasks that orchestrate other tasks. Those tasks are handled by the Executor.
 */
public interface FlowableTask <T extends Output> {
    @Schema(
        title = "List of tasks to run if any tasks failed on this FlowableTask."
    )
    @PluginProperty
    List<Task> getErrors();

    /**
     * Create the topology representation of a flowable task.
     * <p>
     * A flowable task always contains subtask to it returns a cluster that displays the subtasks.
     */
    GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException;

    /**
     * @return all child tasks including errors
     */
    List<Task> allChildTasks();

    /**
     * Resolve child tasks of a flowable task.
     * <p>
     * For a normal flowable, it should be the list of its tasks, for an iterative flowable (such as EachSequential, ForEachItem, ...),
     * it should be the list of its tasks for all iterations.
     */
    List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException;

    /**
     * Resolve next tasks to run for an execution.
     * <p>
     * For a normal flowable, it should be <b>the</b> subsequent task, for a parallel flowable (such as Parallel, ForEachItem, ...),
     * it should be a list of the next subsequent tasks of the size of the concurrency of the task.
     */
    List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException;

    /**
     * Whether the task is allowed to fail.
     */
    boolean isAllowFailure();

    /**
     * Resolve the state of a flowable task.
     */
    default Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun,
            runContext,
            isAllowFailure()
        );
    }

    default T outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return null;
    }
}
