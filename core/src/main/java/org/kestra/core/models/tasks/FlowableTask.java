package org.kestra.core.models.tasks;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

public interface FlowableTask <T extends Output> {
    List<Task> getErrors();

    List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups);

    List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun);

    List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun);

    default Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    default T outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return null;
    }
}
