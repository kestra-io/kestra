package org.floworc.core.runners;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.FlowableTask;
import org.floworc.core.models.tasks.FlowableResult;
import org.floworc.core.models.tasks.Task;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FlowableUtils {
    public static FlowableResult getNexts(RunContext runContext, Execution execution, List<Task> tasks, List<Task> errors) {
        List<Task> currentTasks = execution.findTaskDependingFlowState(tasks, errors);

        // all done, leave
        if (execution.isTerminated(currentTasks)) {
            return FlowableResult.builder()
                .result(FlowableResult.Result.ENDED)
                .childState(execution.hasFailed(tasks) ? State.Type.FAILED : State.Type.SUCCESS)
                .build();
        }

        // first one
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks);
        if (taskRuns.size() == 0) {
            return FlowableResult.builder()
                .nexts(Collections.singletonList(currentTasks.get(0).toTaskRun(execution)))
                .result(FlowableResult.Result.NEXTS)
                .build();
        }

        // find last created
        Optional<TaskRun> lastCreated = execution.findLastByState(currentTasks, State.Type.CREATED);
        if (lastCreated.isPresent()) {
            return FlowableResult.builder()
                .result(FlowableResult.Result.WAIT)
                .build();
        }

        // find first running and maybe handle child tasks
        Optional<TaskRun> firstRunning = execution.findFirstRunning(currentTasks);
        if (firstRunning.isPresent()) {
            return handleChilds(runContext, execution, firstRunning.get(), currentTasks);
        }

        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(currentTasks);
        if (lastTerminated.isPresent()) {
            int lastIndex = taskRuns.indexOf(lastTerminated.get());

            if (currentTasks.size() > lastIndex - 1) {
                return FlowableResult.builder()
                    .nexts(Collections.singletonList(currentTasks.get(lastIndex + 1).toTaskRun(execution)))
                    .result(FlowableResult.Result.NEXTS)
                    .build();
            }
        }

        // no special case, wait
        return FlowableResult.builder()
            .result(FlowableResult.Result.WAIT)
            .build();
    }

    public static FlowableResult handleChilds(RunContext runContext, Execution execution, TaskRun running, List<Task> currentTasks) {
        Task parent = execution.findTaskByTaskRun(currentTasks, running);

        if (!(parent instanceof FlowableTask)) {
            return FlowableResult.builder()
                .result(FlowableResult.Result.WAIT)
                .build();
        }

        FlowableTask flowableParent = (FlowableTask) parent;
        FlowableResult childs = flowableParent.nexts(runContext, execution);

        if (childs.getResult() == FlowableResult.Result.ENDED) {
            return FlowableResult.builder()
                .result(FlowableResult.Result.ENDED)
                .childTask(parent)
                .childTaskRun(running)
                .childState(childs.getChildState())
                .build();
        } else {
            return childs;
        }
    }
}
