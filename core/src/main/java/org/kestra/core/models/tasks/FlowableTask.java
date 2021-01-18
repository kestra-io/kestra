package org.kestra.core.models.tasks;

import io.swagger.v3.oas.annotations.media.Schema;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.NextTaskRun;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.GraphCluster;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

public interface FlowableTask <T extends Output> {
    @Schema(
        title = "List of tasks to run if any tasks failed on this FlowableTask"
    )
    List<Task> getErrors();

    GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException;

    List<Task> allChildTasks();

    List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException;

    List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException;

    default Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    default T outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return null;
    }
}
