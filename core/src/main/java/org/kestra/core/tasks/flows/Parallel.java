package org.kestra.core.tasks.flows;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Parallel extends Task implements FlowableTask {
    private Integer concurrent;

    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

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

        /*
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );

        // all tasks run
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);

        // find all not created tasks
        List<ResolvedTask> notFinds = currentTasks
            .stream()
            .filter(resolvedTask -> taskRuns
                .stream()
                .noneMatch(taskRun -> FlowableUtils.equals(resolvedTask, taskRun, parentTaskRun))
            )
            .collect(Collectors.toList());

        if (notFinds.size() > 0) {
            return notFinds
                .stream()
                .map(resolvedTask -> resolvedTask.toTaskRun(execution))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
        */
    }
}
