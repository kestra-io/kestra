package org.kestra.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import javax.validation.Valid;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Sequential extends Task implements FlowableTask {
    @Valid
    private List<Task> tasks;

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        return FlowableUtils.resolveTasks(this.tasks, parentTaskRun);
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
