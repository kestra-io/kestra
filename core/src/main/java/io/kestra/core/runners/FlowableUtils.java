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
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Dag;

import java.util.*;
import java.util.function.BiFunction;
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
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally
    ) {
        return resolveSequentialNexts(execution, tasks, errors, _finally, null);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, parentTaskRun);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        State.Type terminalState
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun, terminalState);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, parentTaskRun);
    }

    private static List<NextTaskRun> innerResolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> currentTasks,
        TaskRun parentTaskRun
    ) {
        // nothing
        if (currentTasks == null || currentTasks.isEmpty() || execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        // first one
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);
        if (taskRuns.isEmpty()) {
            return Collections.singletonList(currentTasks.getFirst().toNextTaskRun(execution));
        }

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastCreated(taskRuns);
        if (lastCreated.isPresent()) {
            return Collections.emptyList();
        }

        // have submitted, leave
        Optional<TaskRun> lastSubmitted = execution.findLastSubmitted(taskRuns);
        if (lastSubmitted.isPresent()) {
            return Collections.emptyList();
        }

        // have running, leave
        Optional<TaskRun> lastRunning = execution.findLastRunning(taskRuns);
        if (lastRunning.isPresent()) {
            return Collections.emptyList();
        }

        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(taskRuns);
        if (lastTerminated.isPresent()) {
            int lastIndex = taskRuns.indexOf(lastTerminated.get());

            if (currentTasks.size() > lastIndex + 1) {
                return Collections.singletonList(currentTasks.get(lastIndex + 1).toNextTaskRun(execution));
            }
        }

        return Collections.emptyList();
    }

    public static List<NextTaskRun> resolveWaitForNext(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun);

        // nothing
        if (currentTasks == null || currentTasks.isEmpty() || execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        // first one
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);
        if (taskRuns.isEmpty()) {
            return Collections.singletonList(
                currentTasks.getFirst().toNextTaskRunIncrementIteration(execution, parentTaskRun.getIteration())
            );
        }

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastCreated(taskRuns);
        if (lastCreated.isPresent()) {
            return Collections.emptyList();
        }

        // have running, leave
        Optional<TaskRun> lastRunning = execution.findLastRunning(taskRuns);
        if (lastRunning.isPresent()) {
            return Collections.emptyList();
        }

        // have submitted, leave
        Optional<TaskRun> lastSubmitted = execution.findLastSubmitted(taskRuns);
        if (lastSubmitted.isPresent()) {
            return Collections.emptyList();
        }


        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(taskRuns);
        if (lastTerminated.isPresent()) {
            int lastIndex = taskRuns.indexOf(lastTerminated.get());

            if (currentTasks.size() > lastIndex + 1) {
                return Collections.singletonList(currentTasks.get(lastIndex + 1).toNextTaskRunIncrementIteration(execution, parentTaskRun.getIteration()));
            }
        }

        return Collections.emptyList();
    }

    public static Optional<State.Type> resolveSequentialState(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        RunContext runContext,
        boolean allowFailure,
        boolean allowWarning
    ) {
        if (ListUtils.emptyOnNull(tasks).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .findAny()
            .isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        return resolveState(
            execution,
            tasks,
            errors,
            _finally,
            parentTaskRun,
            runContext,
            allowFailure,
            allowWarning
        );
    }

    public static Optional<State.Type> resolveState(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        RunContext runContext,
        boolean allowFailure,
        boolean allowWarning
    ) {
        return resolveState(
            execution,
            tasks,
            errors,
            _finally,
            parentTaskRun,
            runContext,
            allowFailure,
            allowWarning,
            State.Type.SUCCESS
        );
    }

    public static Optional<State.Type> resolveState(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        RunContext runContext,
        boolean allowFailure,
        boolean allowWarning,
        State.Type terminalState
    ) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun, terminalState);

        if (currentTasks == null) {
            runContext.logger().warn(
                "No task found on flow '{}', task '{}', execution '{}'",
                execution.getNamespace() + "." + execution.getFlowId(),
                parentTaskRun.getTaskId(),
                execution.getId()
            );

            return Optional.of(allowFailure ? allowWarning ? State.Type.SUCCESS : State.Type.WARNING : State.Type.FAILED);
        } else if (currentTasks.stream().allMatch(t -> t.getTask().getDisabled()) && !currentTasks.isEmpty()) {
            // if all child tasks are disabled, we end in the terminal state
            return Optional.of(terminalState);
        } else if (!currentTasks.isEmpty()) {
            // handle nominal case, tasks or errors flow are ready to be analyzed
            if (execution.isTerminated(currentTasks, parentTaskRun)) {
                return Optional.of(execution.guessFinalState(tasks, parentTaskRun, allowFailure, allowWarning, terminalState));
            }
        } else {
            // first call, the error flow is not ready, we need to notify the parent task that can be failed to init error flows
            if (execution.hasFailedNoRetry(tasks, parentTaskRun) || terminalState == State.Type.FAILED) {
                return Optional.of(execution.guessFinalState(tasks, parentTaskRun, allowFailure, allowWarning, terminalState));
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
            .toList();
    }

    /**
     * resolveParallelNexts will resolve both concurrent values and subtasks
     * For only concurrent values, see resolveConcurrentNexts()
     */
    public static List<NextTaskRun> resolveParallelNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        Integer concurrency
    ) {
        return resolveParallelNexts(
            execution,
            tasks,
            errors,
            _finally,
            parentTaskRun,
            concurrency,
            (nextTaskRunStream, taskRuns) -> nextTaskRunStream
        );
    }

    /**
     * resolveConcurrentNexts will resolve concurrent values
     * For both concurrent values and subtasks, see resolveParallelNexts()
     */
    public static List<NextTaskRun> resolveConcurrentNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        Integer concurrency
    ) {
        if (execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        List<ResolvedTask> allTasks = execution.findTaskDependingFlowState(
            tasks,
            errors,
            _finally,
            parentTaskRun
        );

        boolean isTasks = tasks.equals(allTasks);

        // errors & finally must be run as sequential tasks
        if (!isTasks) {
            return resolveSequentialNexts(
                execution,
                tasks,
                errors,
                _finally,
                parentTaskRun
            );
        }

        // all tasks run
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(allTasks, parentTaskRun);

        // find all non-terminated
        long nonTerminatedCount = taskRuns
            .stream()
            .filter(taskRun -> !taskRun.getState().isTerminated())
            .count();

        if (concurrency > 0 && nonTerminatedCount >= concurrency) {
            return Collections.emptyList();
        }

        Map<String, List<ResolvedTask>> collect = allTasks
            .stream()
            .collect(Collectors.groupingBy(ResolvedTask::getValue, LinkedHashMap::new, Collectors.toList()));

        long resolvedConcurrency = concurrency == 0 ? Integer.MAX_VALUE : concurrency;
        // if concurrencyLimit > values.size() we limit concurrency to values.size()
        if (resolvedConcurrency > collect.size()) {
            resolvedConcurrency = collect.size();
        }
        long concurrencySlots = resolvedConcurrency - nonTerminatedCount;

        // first one
        if (taskRuns.isEmpty()) {
            return collect.values().stream()
                .limit(concurrencySlots)
                .map(resolvedTasks -> resolvedTasks.getFirst().toNextTaskRun(execution))
                .toList();
        }

        // start as many tasks as we have concurrency slots
        return collect.values().stream()
            .map(resolvedTasks -> resolveSequentialNexts(execution, resolvedTasks, null, null, parentTaskRun))
            .filter(resolvedTasks -> !resolvedTasks.isEmpty())
            .limit(concurrencySlots)
            .map(resolvedTasks -> resolvedTasks.getFirst())
            .toList();
    }

    public static List<NextTaskRun> resolveDagNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        Integer concurrency,
        List<Dag.DagTask> taskDependencies
    ) {
        return resolveParallelNexts(
            execution,
            tasks,
            errors,
            _finally,
            parentTaskRun,
            concurrency,
            (nextTaskRunStream, taskRuns) -> nextTaskRunStream
                .filter(nextTaskRun -> {
                    Task task = nextTaskRun.getTask();
                    List<String> taskDependIds = taskDependencies
                        .stream()
                        .filter(taskDepend -> taskDepend
                            .getTask()
                            .getId()
                            .equals(task.getId())
                        )
                        .findFirst()
                        .map(Dag.DagTask::getDependsOn)
                        .orElse(null);

                    // Check if have no dependencies OR all dependencies are terminated
                    return taskDependIds == null ||
                        new HashSet<>(taskRuns
                            .stream()
                            .filter(taskRun -> taskRun.getState().isTerminated())
                            .map(TaskRun::getTaskId).toList()
                        )
                            .containsAll(taskDependIds);
                })
        );
    }

    public static List<NextTaskRun> resolveParallelNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        Integer concurrency,
        BiFunction<Stream<NextTaskRun>, List<TaskRun>, Stream<NextTaskRun>> nextTaskRunFunction
    ) {
        if (execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            tasks,
            errors,
            _finally,
            parentTaskRun
        );

        boolean isTasks = tasks.equals(currentTasks);

        // errors & finally must be run as sequential tasks
        if (!isTasks) {
            return resolveSequentialNexts(
                execution,
                tasks,
                errors,
                _finally,
                parentTaskRun
            );
        }

        // all tasks run
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);

        // find all running and deal concurrency
        long runningCount = taskRuns
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .count();

        if (concurrency > 0 && runningCount > concurrency) {
            return Collections.emptyList();
        }

        // find all not created tasks
        List<ResolvedTask> notFinds = currentTasks
            .stream()
            .filter(resolvedTask -> taskRuns
                .stream()
                .noneMatch(taskRun -> FlowableUtils.isTaskRunFor(resolvedTask, taskRun, parentTaskRun))
            )
            .toList();

        // first created, leave
        Optional<TaskRun> lastCreated = execution.findLastCreated(taskRuns);

        if (!notFinds.isEmpty() && lastCreated.isEmpty()) {
            Stream<NextTaskRun> nextTaskRunStream = notFinds
                .stream()
                .map(resolvedTask -> resolvedTask.toNextTaskRun(execution));

            nextTaskRunStream = nextTaskRunFunction.apply(nextTaskRunStream, taskRuns);

            if (concurrency > 0) {
                nextTaskRunStream = nextTaskRunStream.limit(concurrency - runningCount);
            }


            return nextTaskRunStream.toList();
        }

        return Collections.emptyList();
    }

    private final static TypeReference<List<Object>> TYPE_REFERENCE = new TypeReference<>() {};
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<ResolvedTask> resolveEachTasks(RunContext runContext, TaskRun parentTaskRun, List<Task> tasks, Object value) throws IllegalVariableEvaluationException {
        List<Object> values;

        if (value instanceof String stringValue) {
            String renderValue = runContext.render(stringValue);
            try {
                values = MAPPER.readValue(renderValue, TYPE_REFERENCE);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        } else if (value instanceof List<?> listValue) {
            values = new ArrayList<>(listValue.size());
            for (Object obj : (List<Object>) value) {
                if (obj instanceof String stringObj) {
                    values.add(runContext.render(stringObj));
                }
                else if (obj instanceof Integer) {
                    values.add(runContext.render(obj.toString()));
                }
                else if(obj instanceof Map mapObj) {
                    //JSON or YAML map
                    values.add(runContext.render(mapObj));
                } else {
                    throw new IllegalVariableEvaluationException("Unknown value element type: " + obj.getClass());
                }
            }
        } else {
            throw new IllegalVariableEvaluationException("Unknown value type: " + value.getClass());
        }

        List<Object> distinctValue = values
            .stream()
            .distinct()
            .toList();

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

        int iteration = 0;
        for (Object current : distinctValue) {
            try {
                String resolvedValue = current instanceof String stringValue ? stringValue : MAPPER.writeValueAsString(current);
                for (Task task : tasks) {
                    result.add(ResolvedTask.builder()
                        .task(task)
                        .value(resolvedValue)
                        .iteration(iteration)
                        .parentId(parentTaskRun.getId())
                        .build()
                    );
                }
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
            iteration++;
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
