package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.JacksonMapper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Optional<TaskRun> lastCreated = execution.findLastCreated(currentTasks, parentTaskRun);
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
        TaskRun parentTaskRun,
        RunContext runContext
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, parentTaskRun);

        if (currentTasks == null) {
            runContext.logger().warn(
                "No task found on flow '{}', task '{}', execution '{}'",
                execution.getNamespace() + "." + execution.getFlowId(),
                parentTaskRun.getTaskId(),
                execution.getId()
            );

            return Optional.of(State.Type.FAILED);
        } else if (currentTasks.size() > 0) {
            // handle nominal case, tasks or errors flow are ready to be analysed
            if (execution.isTerminated(currentTasks, parentTaskRun)) {
                return Optional.of(execution.guessFinalState(tasks, parentTaskRun));
            }
        } else {
            // first call, the error flow is not ready, we need to notify the parent task that can be failed to init error flows
            if (execution.hasFailed(tasks, parentTaskRun)) {
                return Optional.of(execution.guessFinalState(tasks, parentTaskRun));
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
        TaskRun parentTaskRun,
        Integer concurrency
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

        // find all running and deal concurrency
        long runningCount = taskRuns
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .count();

        if (concurrency > 0 && runningCount > concurrency) {
            return new ArrayList<>();
        }

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastCreated(currentTasks, parentTaskRun);

        if (notFinds.size() > 0 && lastCreated.isEmpty()) {
            Stream<NextTaskRun> nextTaskRunStream = notFinds
                .stream()
                .map(resolvedTask -> resolvedTask.toNextTaskRun(execution));

            if (concurrency > 0) {
                nextTaskRunStream = nextTaskRunStream.limit(concurrency - runningCount);
            }

            return nextTaskRunStream.collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private final static TypeReference<List<Object>> TYPE_REFERENCE = new TypeReference<>() {};
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();

    public static List<ResolvedTask> resolveEachTasks(RunContext runContext, TaskRun parentTaskRun, List<Task> tasks, String value) throws IllegalVariableEvaluationException {
        String renderValue = runContext.render(value);

        List<Object> values;
        try {
            values = MAPPER.readValue(renderValue, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }

        List<Object> distinctValue = values
            .stream()
            .distinct()
            .collect(Collectors.toList());


        long nullCount = distinctValue
            .stream()
            .filter(Objects::isNull)
            .count();

        if (nullCount > 0) {
            throw new IllegalVariableEvaluationException("Found '" + nullCount + "' null values on Each, " +
                "with values=" + Arrays.toString(values.toArray())
            );
        }

        ArrayList<ResolvedTask> result = new ArrayList<>();

        for (Object current: distinctValue) {
            for( Task task: tasks) {
                try {
                    String resolvedValue = current instanceof String ? (String)current : MAPPER.writeValueAsString(current);

                    result.add(ResolvedTask.builder()
                        .task(task)
                        .value(resolvedValue)
                        .parentId(parentTaskRun.getId())
                        .build()
                    );
                } catch (JsonProcessingException e) {
                    throw new IllegalVariableEvaluationException(e);
                }
            }
        }

        return result;
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
