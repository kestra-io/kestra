package org.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.NextTaskRun;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class FlowableUtils {
    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, null);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, null);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, null);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        TaskRun parentTaskRun
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, parentTaskRun);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, parentTaskRun);
    }

    private static List<NextTaskRun> innerResolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> currentTasks,
        TaskRun parentTaskRun
    ) {
        // nothing
        if (currentTasks == null || currentTasks.size() == 0) {
            return new ArrayList<>();
        }

        // first one
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);
        if (taskRuns.size() == 0) {
            return Collections.singletonList(currentTasks.get(0).toNextTaskRun(execution));
        }

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastByState(currentTasks, State.Type.CREATED, parentTaskRun);
        if (lastCreated.isPresent()) {
            return new ArrayList<>();
        }

        // have running, leave
        Optional<TaskRun> lastRunning = execution.findLastRunning(currentTasks, parentTaskRun);
        if (lastRunning.isPresent()) {
            return new ArrayList<>();
        }

        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(currentTasks, parentTaskRun);
        if (lastTerminated.isPresent()) {
            int lastIndex = taskRuns.indexOf(lastTerminated.get());

            if (currentTasks.size() > lastIndex + 1) {
                return Collections.singletonList(currentTasks.get(lastIndex + 1).toNextTaskRun(execution));
            }
        }

        return new ArrayList<>();
    }

    public static Optional<State.Type> resolveState(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        TaskRun parentTaskRun
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors);

        if (currentTasks == null) {
            log.warn(
                "No task found on flow '{}', task '{}', execution '{}'",
                execution.getNamespace() + "." + execution.getFlowId(),
                parentTaskRun.getTaskId(),
                execution.getId()
            );

            return Optional.of(State.Type.FAILED);
        } else if (currentTasks.size() > 0) {
            // handle nominal case, tasks or errors flow are ready to be analysed
            if (execution.isTerminated(currentTasks, parentTaskRun)) {
                return Optional.of(execution.guessFinalState(currentTasks, parentTaskRun));
            }
        } else {
            // first call, the error flow is not ready, we need to notify the parent task that can be failed to init error flows
            if (execution.hasFailed(tasks, parentTaskRun)) {
                return Optional.of(execution.guessFinalState());
            }
        }

        return Optional.empty();
    }

    public static List<ResolvedTask> resolveTasks(List<Task> tasks, TaskRun parentTaskRun) {
        if (tasks == null) {
            return null;
        }

        return tasks
            .stream()
            .map(task -> ResolvedTask.builder()
                .task(task)
                .parentId(parentTaskRun.getId())
                .build()
            )
            .collect(Collectors.toList());
    }


    public static List<NextTaskRun> resolveParallelNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        TaskRun parentTaskRun
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            tasks,
            errors,
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
                .map(resolvedTask -> resolvedTask.toNextTaskRun(execution))
                .limit(1)
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    public static List<ResolvedTask> resolveEachTasks(RunContext runContext, TaskRun parentTaskRun, List<Task> tasks, String value) throws IllegalVariableEvaluationException {
        ObjectMapper mapper = new ObjectMapper();

        String[] values;

        String renderValue = runContext.render(value);
        try {
            values = mapper.readValue(renderValue, String[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }

        return Arrays
            .stream(values)
            .distinct()
            .flatMap(v -> tasks
                .stream()
                .map(task -> ResolvedTask.builder()
                    .task(task)
                    .value(v)
                    .parentId(parentTaskRun.getId())
                    .build()
                )
            )
            .collect(Collectors.toList());
    }

    public static boolean isTaskRunFor(ResolvedTask resolvedTask, TaskRun taskRun, TaskRun parentTaskRun) {
        return resolvedTask.getTask().getId().equals(taskRun.getTaskId()) &&
            (
                parentTaskRun == null || parentTaskRun.getId().equals(taskRun.getParentTaskRunId())
            ) &&
            (
                resolvedTask.getValue() == null || resolvedTask.getValue().equals(taskRun.getValue())
            );
    }
}
