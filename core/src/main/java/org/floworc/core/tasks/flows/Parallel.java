package org.floworc.core.tasks.flows;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.FlowableResult;
import org.floworc.core.models.tasks.FlowableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.FlowableUtils;
import org.floworc.core.runners.RunContext;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
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
    public List<Task> childTasks() {
        return this.tasks;
    }

    @Override
    public FlowableResult nexts(RunContext runContext, Execution execution) {
        return FlowableUtils.getNexts(runContext, execution, this.tasks, this.errors);
        /*
        List<Task> currentTasks = execution.findTaskDependingFlowState(tasks, errors);

        // all done, leave
        if (execution.isTerminated(currentTasks)) {
            return FlowableResult.builder()
                .result(FlowableResult.Result.ENDED)
                .childState(execution.hasFailed(this.tasks) ? State.Type.FAILED : State.Type.SUCCESS)
                .build();
        }

        // find all not created tasks
        List<Task> notFind = currentTasks
            .stream()
            .filter(task -> execution
                .getTaskRunList()
                .stream()
                .noneMatch(taskRun -> taskRun.getTaskId().equals(task.getId()))
            )
            .collect(Collectors.toList());

        // create all tasks not created yet
        if (notFind.size() > 0) {
            return FlowableResult.builder()
                .nexts(notFind
                    .stream()
                    .map(task -> task.toTaskRun(execution))
                    .collect(Collectors.toList())
                )
                .result(FlowableResult.Result.NEXTS)
                .build();
        }

        // find first running and maybe handle child tasks
        Optional<TaskRun> firstRunning = execution.findFirstRunning(currentTasks);
        if (firstRunning.isPresent()) {
            return FlowableUtils.handleChilds(runContext, execution, firstRunning.get(), currentTasks);
        }

        // no special case, wait
        return FlowableResult.builder()
            .result(FlowableResult.Result.WAIT)
            .build();
        */
    }
}
