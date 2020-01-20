package org.kestra.core.models.tasks;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import java.util.List;
import java.util.Map;
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

    default Map<String, Object> outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return null;
    }
}
