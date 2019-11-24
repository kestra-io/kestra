package org.floworc.core.models.tasks;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.runners.FlowableUtils;
import org.floworc.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

public interface FlowableTask {
    List<Task> getErrors();

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
}
