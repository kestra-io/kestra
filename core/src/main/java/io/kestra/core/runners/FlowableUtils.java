package io.kestra.core.runners;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Dag;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.NonNull;

import static io.kestra.core.utils.Rethrow.throwFunction;

public class FlowableUtils {
    private final static TypeReference<List<Object>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson(true);

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks) {
        List<ResolvedTask> currentTasks = execution.removeDisabled(tasks);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, null);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally) {
        return resolveSequentialNexts(execution, tasks, errors, _finally, null);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, parentTaskRun);
    }

    public static List<NextTaskRun> resolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> tasks,
        List<ResolvedTask> errors,
        List<ResolvedTask> _finally,
        TaskRun parentTaskRun,
        State.Type terminalState) {
        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(tasks, errors, _finally, parentTaskRun, terminalState);

        return FlowableUtils.innerResolveSequentialNexts(execution, currentTasks, parentTaskRun);
    }

    private static List<NextTaskRun> innerResolveSequentialNexts(
        Execution execution,
        List<ResolvedTask> currentTasks,
        TaskRun parentTaskRun) {
        // nothing
        if (currentTasks == null || currentTasks.isEmpty() || execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        // first one
        List<TaskRun> taskRuns = execution.findTaskRunByTasks(currentTasks, parentTaskRun);
        if (taskRuns.isEmpty()) {
            return Collections.singletonList(currentTasks.getFirst().toNextTaskRun(execution));
        }

        // if it has any created/submitted or running, we leave
        if (taskRuns.stream()
            .anyMatch(taskRun -> taskRun.getState().isCreated()  || taskRun.getState().getCurrent() == State.Type.SUBMITTED || taskRun.getState().isRunning())
        ) {
            return Collections.emptyList();
        }

        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(taskRuns);
        if (lastTerminated.isPresent()) {
            int lastIndex = indexOfLastTerminatedInTasks(currentTasks, lastTerminated.get(), parentTaskRun);
            if (lastIndex >= 0 && currentTasks.size() > lastIndex + 1) {
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
        TaskRun parentTaskRun) {
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

        // if it has any created/submitted or running, we leave
        if (taskRuns.stream()
            .anyMatch(taskRun -> taskRun.getState().isCreated()  || taskRun.getState().getCurrent() == State.Type.SUBMITTED || taskRun.getState().isRunning())
        ) {
            return Collections.emptyList();
        }

        // last success, find next
        Optional<TaskRun> lastTerminated = execution.findLastTerminated(taskRuns);
        if (lastTerminated.isPresent()) {
            int lastIndex = indexOfLastTerminatedInTasks(currentTasks, lastTerminated.get(), parentTaskRun);
            if (lastIndex >= 0 && currentTasks.size() > lastIndex + 1) {
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
        boolean allowWarning) {
        if (
            ListUtils.emptyOnNull(tasks).stream()
                .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
                .findAny()
                .isEmpty()
        ) {
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
        boolean allowWarning) {
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
        State.Type terminalState) {
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
            .map(
                task -> ResolvedTask.builder()
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
        Integer concurrency) {
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
        Integer concurrency) {
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
        List<Dag.DagTask> taskDependencies) {
        return resolveParallelNexts(
            execution,
            tasks,
            errors,
            _finally,
            parentTaskRun,
            concurrency,
            (nextTaskRunStream, taskRuns) -> nextTaskRunStream
                .filter(nextTaskRun ->
                {
                    Task task = nextTaskRun.getTask();
                    List<String> taskDependIds = taskDependencies
                        .stream()
                        .filter(
                            taskDepend -> taskDepend
                                .getTask()
                                .getId()
                                .equals(task.getId())
                        )
                        .findFirst()
                        .map(Dag.DagTask::getDependsOn)
                        .orElse(null);

                    // Check if have no dependencies OR all dependencies are terminated
                    return taskDependIds == null ||
                        new HashSet<>(
                            taskRuns
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
        BiFunction<Stream<NextTaskRun>, List<TaskRun>, Stream<NextTaskRun>> nextTaskRunFunction) {
        if (execution.getState().getCurrent() == State.Type.KILLING) {
            return Collections.emptyList();
        }

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            tasks,
            errors,
            _finally,
            parentTaskRun
        );

        List<ResolvedTask> resolvedTasks = execution.removeDisabled(tasks);

        boolean isTasks = resolvedTasks.equals(currentTasks);

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
            .filter(
                resolvedTask -> taskRuns
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

    /**
     * Resolves the values, then for each value create a {@link ResolvedTask} with it.
     *
     * @see #resolveValues(RunContext, Object)
     */
    public static List<ResolvedTask> resolveEachTasks(RunContext runContext, TaskRun parentTaskRun, List<Task> tasks, Object value) throws IllegalVariableEvaluationException {
        var either = resolveValues(runContext, value);
        if (either.isRight()) {
            throw new IllegalArgumentException("Maps are not supported in values");
        }
        List<String> distinctValue = either.getLeft();

        ArrayList<ResolvedTask> result = new ArrayList<>();

        int iteration = 0;
        for (String current : distinctValue) {
            for (Task task : tasks) {
                result.add(
                    ResolvedTask.builder()
                        .task(task)
                        .value(current)
                        .iteration(iteration)
                        .parentId(parentTaskRun.getId())
                        .build()
                );
            }

            iteration++;
        }

        return result;
    }

    /**
     * Resolves a single Object values to a List of String representation.
     * It supports:
     * - A String that will be rendered then parsed as a JSON array or a JSON object (list or map, see under).
     * - A List of Objects that will be converted to a List of String, each object being rendered then parsed as a JSON object.
     * - A Map of String to Object that will be converted to a List of pairs of String/String, each object being rendered then parsed as a JSON object.
     *
     * @return a list of String with no duplicates if the values were a list, or a list of pairs of String/String if the values were a map.
     * @throws IllegalVariableEvaluationException in case of JSON error, unsupported value type or duplicate values.
     */
    public static Either<List<String>, List<Pair<String, String>>> resolveValues(RunContext runContext, Object values) throws IllegalVariableEvaluationException {
        if (values instanceof String stringValue) {
            String renderValue = runContext.render(stringValue);
            try {
                JsonNode valuesNode = MAPPER.readTree(renderValue);
                if (valuesNode.isArray()) {
                    List<String> resolvedValues = MAPPER.convertValue(valuesNode, TYPE_REFERENCE)
                        .stream()
                        .map(throwFunction(obj -> {
                            if (obj instanceof String s) {
                                return s;
                            } else if (obj == null) {
                                throw new IllegalVariableEvaluationException(
                                    "Found a null value inside the iteration values=" + serializeAsString(values)
                                );
                            } else {
                                return serializeAsString(obj);
                            }
                        }))
                        .distinct()
                        .toList();
                    return Either.left(resolvedValues);
                } else if (valuesNode.isObject()) {
                    List<Pair<String, String>> resolvedValues = new ArrayList<>();
                    Map<String, Object> mapValues = MAPPER.convertValue(valuesNode, JacksonMapper.MAP_TYPE_REFERENCE);
                    for (var entry : mapValues.entrySet()) {
                        resolvedValues.add(Pair.of(entry.getKey(), valueAsString(runContext, values, entry.getValue())));
                    }
                    return Either.right(resolvedValues);
                } else {
                    throw new IllegalVariableEvaluationException("Unknown value type: " + valuesNode.getNodeType());
                }

            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        } else if (values instanceof List<?> listValue) {
            List<String> resolvedValues = new ArrayList<>(listValue.size());
            for (Object obj : listValue) {
                resolvedValues.add(valueAsString(runContext, values, obj));
            }
            return Either.left(resolvedValues.stream().distinct().toList());
        } else if (values instanceof Map<?, ?> mapValue) {
          List<Pair<String, String>> resolvedValues = new ArrayList<>();
            for (var entry : ((Map<String, Object>) mapValue).entrySet()) {
                resolvedValues.add(Pair.of(entry.getKey(), valueAsString(runContext, values, entry.getValue())));
            }
            return Either.right(resolvedValues);
        } else {
            throw new IllegalVariableEvaluationException("Unknown value type: " + values.getClass());
        }
    }

    private static String valueAsString(RunContext runContext, Object value, Object obj) throws IllegalVariableEvaluationException {
        return switch (obj) {
            case String stringObj -> runContext.render(stringObj);
            case Number number -> runContext.render(number.toString());
            case Map<?, ?> mapObj -> serializeAsString(runContext.render((Map<String, Object>) mapObj)); //JSON or YAML map
            case null -> throw new IllegalVariableEvaluationException(
                "Found a null value inside the iteration values=" + serializeAsString(value)
            );
            default -> throw new IllegalVariableEvaluationException("Unknown value element type: " + obj.getClass());
        };
    }

    private static String serializeAsString(Object obj) throws IllegalVariableEvaluationException {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }
    }

    /**
     * Returns the index of the given {@code lastTerminated} task run within {@code currentTasks},
     * matching by task ID (and optionally by parent/value via {@link #isTaskRunFor}).
     * Using this index instead of the position in the raw task-run list avoids off-by-N skipping
     * when a task produces multiple task runs (e.g. WaitFor creates one per iteration).
     *
     * @return the 0-based index, or {@code -1} if not found
     */
    private static int indexOfLastTerminatedInTasks(List<ResolvedTask> currentTasks, TaskRun lastTerminated, TaskRun parentTaskRun) {
        return IntStream.range(0, currentTasks.size())
            .filter(i -> FlowableUtils.isTaskRunFor(currentTasks.get(i), lastTerminated, parentTaskRun))
            .findFirst()
            .orElse(-1);
    }

    public static boolean isTaskRunFor(ResolvedTask resolvedTask, TaskRun taskRun, TaskRun parentTaskRun) {
        return resolvedTask.getTask().getId().equals(taskRun.getTaskId()) &&
            (parentTaskRun == null || parentTaskRun.getId().equals(taskRun.getParentTaskRunId())) &&
            (resolvedTask.getValue() == null || resolvedTask.getValue().equals(taskRun.getValue()));
    }
}
