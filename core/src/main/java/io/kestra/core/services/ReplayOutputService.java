package io.kestra.core.services;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.OutputOverride;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.PebbleUtil;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.ExtensionRegistry;
import io.pebbletemplates.pebble.lexer.LexerImpl;
import io.pebbletemplates.pebble.node.ArgumentsNode;
import io.pebbletemplates.pebble.node.NamedArgumentNode;
import io.pebbletemplates.pebble.node.PositionalArgumentNode;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.RootNode;
import io.pebbletemplates.pebble.node.TextNode;
import io.pebbletemplates.pebble.node.expression.ArrayExpression;
import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.node.expression.ContextVariableExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.node.expression.FilterInvocationExpression;
import io.pebbletemplates.pebble.node.expression.FunctionOrMacroInvocationExpression;
import io.pebbletemplates.pebble.node.expression.GetAttributeExpression;
import io.pebbletemplates.pebble.node.expression.LiteralStringExpression;
import io.pebbletemplates.pebble.node.expression.MapExpression;
import io.pebbletemplates.pebble.node.expression.TernaryExpression;
import io.pebbletemplates.pebble.node.expression.UnaryExpression;
import io.pebbletemplates.pebble.parser.ParserImpl;
import io.pebbletemplates.pebble.parser.ParserOptions;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Lists the outputs of previous tasks that a task reads in its own properties through a static path, such as
 * {@code outputs.fetch.values.code}, and turns the values a user sets for them on replay into {@link OutputOverride}s.
 */
@Slf4j
@Singleton
public class ReplayOutputService {
    private static final String OUTPUTS = "outputs";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final PebbleEngine pebbleEngine;
    private final TaskOutputService taskOutputService;
    private final ExecutionService executionService;

    @Inject
    public ReplayOutputService(PebbleEngineFactory pebbleEngineFactory, TaskOutputService taskOutputService, ExecutionService executionService) {
        this.pebbleEngine = Objects.requireNonNull(pebbleEngineFactory).create();
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.executionService = Objects.requireNonNull(executionService);
    }

    /**
     * The outputs the task of {@code taskRunId} reads in its own properties, outside {@code {% %}} blocks and not in the
     * tasks nested in it, with their value in the execution. Only outputs of task runs a replay reuses are listed.
     * Each one names the other tasks the replay may run that read it, or a key in it, through a static path.
     */
    public ReplayOutputs outputs(Flow flow, Execution execution, String taskRunId) throws InternalException {
        ExecutionService.ReplayScope scope = executionService.replayScope(execution, flow, taskRunId);
        List<Candidate> candidates = candidates(flow, execution, taskRunId, scope);
        if (candidates.isEmpty()) {
            return new ReplayOutputs(List.of(), List.of());
        }

        Map<String, Reads> reads = new LinkedHashMap<>();
        for (Task task : tasksRunByReplay(flow, execution, execution.findTaskRunByTaskRunId(taskRunId), scope)) {
            reads.put(task.getId(), reads(task, 2));
        }

        List<ReplayOutput> values = candidates.stream()
            .map(candidate -> new ReplayOutput(candidate.path(), candidate.value(), readers(candidate.segments(), reads)))
            .toList();
        List<String> dynamicReaders = reads.entrySet().stream()
            .filter(entry -> entry.getValue().dynamic)
            .map(Map.Entry::getKey)
            .toList();
        return new ReplayOutputs(values, dynamicReaders);
    }

    /**
     * @throws IllegalArgumentException if a key is not an output the task reads, or if no value differs from the current one
     */
    public List<OutputOverride> overrides(Flow flow, Execution execution, String taskRunId, Map<String, Object> requested) throws InternalException {
        Map<String, Candidate> candidates = candidates(flow, execution, taskRunId, executionService.replayScope(execution, flow, taskRunId)).stream()
            .collect(Collectors.toMap(Candidate::path, Function.identity()));

        List<OutputOverride> overrides = new ArrayList<>(requested.size());
        for (Map.Entry<String, Object> entry : requested.entrySet()) {
            Candidate candidate = staticPath(entry.getKey()).map(ReplayOutputService::format).map(candidates::get).orElseThrow(
                () -> new IllegalArgumentException(
                    "Cannot override '%s': the task of task run '%s' does not read it through a static path in its own properties.".formatted(entry.getKey(), taskRunId)
                )
            );
            if (!Objects.equals(candidate.value(), entry.getValue())) {
                overrides.add(new OutputOverride(candidate.path(), candidate.taskRun().getId(), candidate.keys(), entry.getValue(), candidate.value()));
            }
        }
        if (overrides.isEmpty()) {
            throw new IllegalArgumentException("Cannot replay task run '%s' with overrides: no value differs from the current output.".formatted(taskRunId));
        }
        return overrides;
    }

    private List<Candidate> candidates(Flow flow, Execution execution, String taskRunId, ExecutionService.ReplayScope scope) throws InternalException {
        TaskRun replayed = execution.findTaskRunByTaskRunId(taskRunId);
        Set<List<String>> paths = reads(flow.findTaskByTaskId(replayed.getTaskId()), 3).paths;
        if (paths.isEmpty()) {
            return List.of();
        }

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        List<Candidate> candidates = new ArrayList<>(paths.size());
        for (List<String> path : paths) {
            target(execution, path)
                .filter(target -> !isReplayed(execution, target.getKey(), replayed) && !scope.toRemove().contains(target.getKey().getId()))
                .ifPresent(target -> candidates.add(new Candidate(format(path), path, target.getKey(), target.getValue(), valueAt(outputs, path.subList(1, path.size())))));
        }
        return candidates;
    }

    private static List<Task> tasksRunByReplay(Flow flow, Execution execution, TaskRun replayed, ExecutionService.ReplayScope scope) {
        Map<String, List<TaskRun>> taskRuns = ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
            .collect(Collectors.groupingBy(TaskRun::getTaskId));
        Map<String, Task> tasks = new LinkedHashMap<>();
        for (Task task : flow.allTasksWithChilds()) {
            if (!task.getId().equals(replayed.getTaskId()) && mayRun(flow, task, taskRuns, scope)) {
                tasks.putIfAbsent(task.getId(), task);
            }
        }
        return List.copyOf(tasks.values());
    }

    // a task that never ran may still run, unless its parent ran and the replay reuses it as it is
    private static boolean mayRun(Flow flow, Task task, Map<String, List<TaskRun>> taskRuns, ExecutionService.ReplayScope scope) {
        List<TaskRun> runs = taskRuns.getOrDefault(task.getId(), List.of());
        if (!runs.isEmpty()) {
            return runs.stream().anyMatch(taskRun -> scope.toRestart().contains(taskRun.getId()) || scope.toRemove().contains(taskRun.getId()));
        }
        Task parent = flow.findParentTasksByTaskId(task.getId());
        return parent == null || mayRun(flow, parent, taskRuns, scope);
    }

    // a reader of the path, of a map holding it, or of a key inside it sees the overridden value
    private static List<OutputReader> readers(List<String> path, Map<String, Reads> reads) {
        List<OutputReader> readers = new ArrayList<>();
        reads.forEach((taskId, read) -> read.paths.stream()
            .filter(other -> isPrefix(other, path) || isPrefix(path, other))
            .forEach(other -> readers.add(new OutputReader(taskId, format(other)))));
        return readers;
    }

    private static boolean isPrefix(List<String> prefix, List<String> path) {
        return prefix.size() <= path.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private Reads reads(Task task, int minSize) {
        Reads reads = new Reads(minSize);
        for (String template : templates(task)) {
            parse(template).ifPresent(root -> root.getBody().getChildren().forEach(node -> {
                if (node instanceof PrintNode print) {
                    collect(print.getExpression(), reads);
                } else if (!(node instanceof TextNode) && template.contains(OUTPUTS)) {
                    reads.dynamic = true;
                }
            }));
        }
        return reads;
    }

    private static void collect(Expression<?> expression, Reads reads) {
        if (expression instanceof ContextVariableExpression variable && OUTPUTS.equals(variable.getName())) {
            reads.dynamic = true;
            return;
        }
        Optional<List<String>> path = expression instanceof GetAttributeExpression ? staticPath(expression) : Optional.empty();
        if (path.isPresent()) {
            if (path.get().size() >= reads.minSize && OUTPUTS.equals(path.get().getFirst())) {
                reads.paths.add(path.get());
            }
            return;
        }
        if (expression instanceof GetAttributeExpression attribute) {
            collectInDynamicChain(attribute, reads);
            return;
        }
        children(expression).forEach(child -> collect(child, reads));
    }

    // a static prefix of a dynamic path is not what the task reads, so only the expressions inside the chain are collected
    private static void collectInDynamicChain(GetAttributeExpression attribute, Reads reads) {
        collect(attribute.getAttributeNameExpression(), reads);
        arguments(attribute.getArgumentsNode()).forEach(argument -> collect(argument, reads));
        if (attribute.getNode() instanceof GetAttributeExpression parent) {
            collectInDynamicChain(parent, reads);
        } else {
            collect(attribute.getNode(), reads);
        }
    }

    private static Optional<List<String>> staticPath(Expression<?> expression) {
        if (expression instanceof ContextVariableExpression variable) {
            return Optional.of(List.of(variable.getName()));
        }
        if (expression instanceof GetAttributeExpression attribute && isEmpty(attribute.getArgumentsNode())
            && attribute.getAttributeNameExpression() instanceof LiteralStringExpression name) {
            return staticPath(attribute.getNode())
                .map(parent -> Stream.concat(parent.stream(), Stream.of(name.getValue())).toList());
        }
        return Optional.empty();
    }

    private Optional<List<String>> staticPath(String key) {
        return parse("{{ %s }}".formatted(key))
            .filter(root -> root.getBody().getChildren().size() == 1 && root.getBody().getChildren().getFirst() instanceof PrintNode)
            .flatMap(root -> staticPath(((PrintNode) root.getBody().getChildren().getFirst()).getExpression()));
    }

    private static List<Expression<?>> children(Expression<?> expression) {
        List<Expression<?>> children = new ArrayList<>();
        switch (expression) {
            case BinaryExpression<?> binary -> {
                children.add(binary.getLeftExpression());
                children.add(binary.getRightExpression());
            }
            case UnaryExpression unary -> children.add(unary.getChildExpression());
            case TernaryExpression ternary -> children.addAll(List.of(ternary.getExpression1(), ternary.getExpression2(), ternary.getExpression3()));
            case GetAttributeExpression attribute -> {
                children.add(attribute.getNode());
                children.add(attribute.getAttributeNameExpression());
                children.addAll(arguments(attribute.getArgumentsNode()));
            }
            case FilterInvocationExpression filter -> children.addAll(arguments(filter.getArgs()));
            case FunctionOrMacroInvocationExpression function -> children.addAll(arguments(function.getArguments()));
            case ArrayExpression array -> children.addAll(array.getValues());
            case MapExpression map -> map.getEntries().forEach((key, value) -> {
                children.add(key);
                children.add(value);
            });
            default -> {
            }
        }
        children.removeIf(Objects::isNull);
        return children;
    }

    private static List<Expression<?>> arguments(@Nullable ArgumentsNode arguments) {
        if (arguments == null) {
            return List.of();
        }
        return Stream.concat(
            arguments.getPositionalArgs() == null ? Stream.empty() : arguments.getPositionalArgs().stream().map(PositionalArgumentNode::getValueExpression),
            arguments.getNamedArgs() == null ? Stream.empty() : arguments.getNamedArgs().stream().map(NamedArgumentNode::getValueExpression)
        ).<Expression<?>>map(expression -> expression).toList();
    }

    private static boolean isEmpty(@Nullable ArgumentsNode arguments) {
        return arguments(arguments).isEmpty();
    }

    // computeOutputs nests the outputs of a task run under the values of its parents and its own, as `outputs.task.value.key`
    private static Optional<Map.Entry<TaskRun, List<String>>> target(Execution execution, List<String> path) {
        List<String> rest = path.subList(2, path.size());
        for (TaskRun taskRun : execution.findTaskRunsByTaskId(path.get(1))) {
            List<String> values = Stream.concat(execution.findParents(taskRun).stream(), Stream.of(taskRun))
                .map(TaskRun::getValue)
                .filter(Objects::nonNull)
                .toList();
            if (rest.size() > values.size() && rest.subList(0, values.size()).equals(values)) {
                return Optional.of(Map.entry(taskRun, List.copyOf(rest.subList(values.size(), rest.size()))));
            }
        }
        return Optional.empty();
    }

    private static boolean isReplayed(Execution execution, TaskRun taskRun, TaskRun replayed) {
        return taskRun.getId().equals(replayed.getId()) || execution.findParents(taskRun).stream().anyMatch(parent -> parent.getId().equals(replayed.getId()));
    }

    @Nullable
    private static Object valueAt(Map<String, Object> outputs, List<String> keys) {
        Object current = outputs;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private static String format(List<String> path) {
        StringBuilder formatted = new StringBuilder(path.getFirst());
        path.stream().skip(1).forEach(key -> formatted.append(
            IDENTIFIER.matcher(key).matches() ? "." + key : "['%s']".formatted(key.replace("\\", "\\\\").replace("'", "\\'"))
        ));
        return formatted.toString();
    }

    private static List<String> templates(Task task) {
        Set<String> childTaskIds = task instanceof FlowableTask<?> flowable
            ? flowable.allChildTasks().stream().map(Task::getId).collect(Collectors.toSet())
            : Set.of();
        Map<String, Object> properties = new LinkedHashMap<>(JacksonMapper.toMap(task));
        properties.remove("description");
        List<String> templates = new ArrayList<>();
        collectTemplates(properties, childTaskIds, templates);
        return templates;
    }

    private static void collectTemplates(Object value, Set<String> childTaskIds, List<String> templates) {
        switch (value) {
            case String string when PebbleUtil.containsOpeningBlockDelimiter(string) -> templates.add(string);
            case Map<?, ?> map when !(map.containsKey("type") && map.get("id") instanceof String id && childTaskIds.contains(id)) ->
                map.values().forEach(child -> collectTemplates(child, childTaskIds, templates));
            case List<?> list -> list.forEach(child -> collectTemplates(child, childTaskIds, templates));
            case null, default -> {
            }
        }
    }

    private Optional<RootNode> parse(String template) {
        ExtensionRegistry registry = pebbleEngine.getExtensionRegistry();
        try {
            LexerImpl lexer = new LexerImpl(pebbleEngine.getSyntax(), registry.getUnaryOperators().values(), registry.getBinaryOperators().values());
            ParserImpl parser = new ParserImpl(registry.getUnaryOperators(), registry.getBinaryOperators(), registry.getTokenParsers(), new ParserOptions());
            return Optional.of(parser.parse(lexer.tokenize(new StringReader(template), "replay-outputs")));
        } catch (PebbleException e) {
            log.debug("Unable to parse '{}' to list the outputs it reads", template, e);
            return Optional.empty();
        }
    }

    /**
     * @param dynamicReaders tasks the replay may run that read outputs as a whole, through a dynamic path or in a {@code {% %}} block
     */
    public record ReplayOutputs(@NotNull List<ReplayOutput> values, @NotNull List<String> dynamicReaders) {
    }

    /**
     * @param path the path as the task reads it, with bracket notation for a key that is not an identifier
     * @param value the output in the execution, {@code null} when the task run produced no such output
     */
    public record ReplayOutput(@NotNull String path, @Nullable Object value, @NotNull List<OutputReader> readers) {
    }

    /**
     * @param path the path the task reads: this output, a map holding it, or a key inside it
     */
    public record OutputReader(@NotNull String taskId, @NotNull String path) {
    }

    private record Candidate(String path, List<String> segments, TaskRun taskRun, List<String> keys, @Nullable Object value) {
    }

    private static final class Reads {
        private final int minSize;
        private final Set<List<String>> paths = new LinkedHashSet<>();
        private boolean dynamic;

        private Reads(int minSize) {
            this.minSize = minSize;
        }
    }
}
