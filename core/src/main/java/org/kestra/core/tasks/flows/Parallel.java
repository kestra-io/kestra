package org.kestra.core.tasks.flows;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Parallel extends Task implements FlowableTask<VoidOutput> {
    private Integer concurrent;

    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        return TreeService.sequential(
            this.tasks,
            this.errors,
            Collections.singletonList(ParentTaskTree.builder()
                .id(this.getId())
                .build()
            ),
            execution,
            groups
        );
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.tasks, parentTaskRun);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        Optional<State.Type> type = FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );

        return type;
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException{
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
                .noneMatch(taskRun -> FlowableUtils.isTaskRunFor(resolvedTask, taskRun, parentTaskRun))
            )
            .collect(Collectors.toList());

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastByState(currentTasks, State.Type.CREATED, parentTaskRun);

        if (notFinds.size() > 0 && lastCreated.isEmpty()) {
            return notFinds
                .stream()
                .map(resolvedTask -> resolvedTask.toTaskRun(execution))
                .limit(1)
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
