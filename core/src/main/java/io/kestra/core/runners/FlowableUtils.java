package io.kestra.core.runners;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
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
import io.kestra.core.models.property.URIFetcher;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Dag;
import org.apache.commons.lang3.tuple.Pair;

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
        switch (values) {
            case String stringValue -> {
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
                } catch (IOException e) {
                    throw new IllegalVariableEvaluationException(e);
                }
            }
            case List<?> listValue -> {
                List<String> resolvedValues = new ArrayList<>(listValue.size());
                for (Object obj : listValue) {
                    resolvedValues.add(valueAsString(runContext, values, obj));
                }
                return Either.left(resolvedValues.stream().distinct().toList());
            }
            case Map<?, ?> mapValue -> {
                List<Pair<String, String>> resolvedValues = new ArrayList<>();
                for (var entry : ((Map<String, Object>) mapValue).entrySet()) {
                    resolvedValues.add(Pair.of(entry.getKey(), valueAsString(runContext, values, entry.getValue())));
                }
                return Either.right(resolvedValues);
            }
            default -> throw new IllegalVariableEvaluationException("Unknown value type: " + values.getClass());
        }
    }

    private static String valueAsString(RunContext runContext, Object values, Object value) throws IllegalVariableEvaluationException {
        return switch (value) {
            case String stringObj -> runContext.render(stringObj);
            case Number number -> runContext.render(number.toString());
            case Map<?, ?> mapObj -> serializeAsString(runContext.render((Map<String, Object>) mapObj)); //JSON or YAML map
            case null -> throw new IllegalVariableEvaluationException(
                "Found a null value inside the iteration values=" + serializeAsString(values)
            );
            default -> throw new IllegalVariableEvaluationException("Unknown value element type: " + value.getClass());
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

    /**
     * Resolves the URI from Loop values if the values expression evaluates to a URI, otherwise returns an empty Optional.
     * The values expression is rendered before checking whether it is a supported URI.
     */
    public static Optional<String> resolveLoopValuesUri(RunContext runContext, Object values) throws IllegalVariableEvaluationException {
        if (!(values instanceof String stringValue)) {
            return Optional.empty();
        }
        String renderValue = runContext.render(stringValue);
        if (URIFetcher.supports(renderValue)) {
            return Optional.of(renderValue);
        }

        return Optional.empty();
    }

    /**
     * Reads up to {@code limit} ION values from the URI-backed ION file and counts the total number of
     * values in a single file pass (no double open). Used during Loop initial creation.
     *
     * @param uri   the rendered URI pointing to the ION file
     * @param limit the maximum number of values to return; pass {@link Integer#MAX_VALUE} to return all
     * @return a record holding the total line count, the first {@code limit} values, and the byte offset
     *         immediately after the last value read
     */
    public static LoopInitialValuesFromUri readAndCountLoopValuesFromUri(RunContext runContext, String uri, int limit) throws IOException, IllegalVariableEvaluationException {
        try (var is = URIFetcher.of(uri).fetch(runContext)) {
            int totalCount = 0;
            List<String> result = new ArrayList<>();
            long currentOffset = 0;
            long nextOffset = 0;
            var lineBuffer = new ByteArrayOutputStream();
            byte[] readBuf = new byte[FileSerde.BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(readBuf)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    currentOffset++;
                    int b = readBuf[i] & 0xFF;
                    if (b == '\n') {
                        if (lineBuffer.size() > 0) {
                            totalCount++;
                            if (result.size() < limit) {
                                result.add(ionLineToString(lineBuffer));
                                nextOffset = currentOffset;
                            }
                            lineBuffer.reset();
                        }
                    } else {
                        lineBuffer.write(b);
                    }
                }
            }
            // handle last line without trailing newline
            if (lineBuffer.size() > 0) {
                totalCount++;
                if (result.size() < limit) {
                    result.add(ionLineToString(lineBuffer));
                    nextOffset = currentOffset;
                }
            }
            return new LoopInitialValuesFromUri(totalCount, result, nextOffset);
        }
    }

    /** Holds the result of a combined count-and-read pass over a URI-backed ION file. */
    public record LoopInitialValuesFromUri(int totalCount, List<String> values, long nextOffset) {}

    /**
     * Reads up to {@code count} ION values from the URI-backed ION file, starting at the given byte offset.
     * Uses a read buffer for efficiency while maintaining accurate byte-offset tracking.
     *
     * @param uri    the rendered URI pointing to the ION file
     * @param offset the byte offset from which to start reading (0 for the beginning of the file)
     * @param count  the maximum number of values to read
     * @return a pair of the parsed string values and the byte offset immediately after the last byte read
     */
    public static Pair<List<String>, Long> readLoopValuesFromUri(RunContext runContext, String uri, long offset, int count) throws IOException, IllegalVariableEvaluationException {
        try (var is = URIFetcher.of(uri).fetch(runContext)) {
            if (offset > 0) {
                is.skipNBytes(offset);
            }

            List<String> result = new ArrayList<>(count);
            long currentOffset = offset;
            var lineBuffer = new ByteArrayOutputStream();
            byte[] readBuf = new byte[FileSerde.BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(readBuf)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    currentOffset++;
                    int b = readBuf[i] & 0xFF;
                    if (b == '\n') {
                        if (lineBuffer.size() > 0) {
                            result.add(ionLineToString(lineBuffer));
                            lineBuffer.reset();
                            if (result.size() == count) {
                                return Pair.of(result, currentOffset);
                            }
                        }
                    } else {
                        lineBuffer.write(b);
                    }
                }
            }

            // handle last line without trailing newline
            if (lineBuffer.size() > 0 && result.size() < count) {
                result.add(ionLineToString(lineBuffer));
            }
            return Pair.of(result, currentOffset);
        }
    }

    /** Parses a single ION line (accumulated in {@code buf}) into a String value. */
    private static String ionLineToString(ByteArrayOutputStream buf) throws IOException, IllegalVariableEvaluationException {
        String line = buf.toString(StandardCharsets.UTF_8);
        Object parsed = JacksonMapper.ofIon().readValue(line, Object.class);
        return switch (parsed) {
            case String s -> s;
            case Number n -> n.toString();
            case null -> throw new IllegalVariableEvaluationException("Found a null value in the ION file");
            default -> serializeAsString(parsed);
        };
    }
}
