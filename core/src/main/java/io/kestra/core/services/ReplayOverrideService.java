package io.kestra.core.services;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunOverride;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.VariablePath;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.PebbleUtil;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.ExtensionRegistry;
import io.pebbletemplates.pebble.lexer.LexerImpl;
import io.pebbletemplates.pebble.node.ArgumentsNode;
import io.pebbletemplates.pebble.node.BodyNode;
import io.pebbletemplates.pebble.node.ForNode;
import io.pebbletemplates.pebble.node.IfNode;
import io.pebbletemplates.pebble.node.Node;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.RootNode;
import io.pebbletemplates.pebble.node.SetNode;
import io.pebbletemplates.pebble.node.expression.ArrayExpression;
import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.node.expression.ContextVariableExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.node.expression.FilterInvocationExpression;
import io.pebbletemplates.pebble.node.expression.FunctionOrMacroInvocationExpression;
import io.pebbletemplates.pebble.node.expression.GetAttributeExpression;
import io.pebbletemplates.pebble.node.expression.LiteralIntegerExpression;
import io.pebbletemplates.pebble.node.expression.LiteralLongExpression;
import io.pebbletemplates.pebble.node.expression.LiteralStringExpression;
import io.pebbletemplates.pebble.node.expression.MapExpression;
import io.pebbletemplates.pebble.node.expression.TernaryExpression;
import io.pebbletemplates.pebble.node.expression.UnaryExpression;
import io.pebbletemplates.pebble.parser.ParserImpl;
import io.pebbletemplates.pebble.parser.ParserOptions;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Lists the values a task reads in its own properties, and turns the values a user sets on replay into
 * {@link TaskRunOverride}s. Only inputs and outputs can be overridden.
 */
@Slf4j
@Singleton
public class ReplayOverrideService {
    private static final String FILE_URI_PREFIX = "kestra://";
    private static final Map<String, Kind> ROOT_KINDS = Map.of(
        "inputs", Kind.INPUT,
        "outputs", Kind.OUTPUT,
        "vars", Kind.FLOW_VARIABLE,
        "trigger", Kind.TRIGGER
    );

    private final PebbleEngine pebbleEngine;
    private final RunContextFactory runContextFactory;
    private final FlowInputOutput flowInputOutput;

    @Inject
    public ReplayOverrideService(PebbleEngineFactory pebbleEngineFactory, RunContextFactory runContextFactory, FlowInputOutput flowInputOutput) {
        this.pebbleEngine = Objects.requireNonNull(pebbleEngineFactory).create();
        this.runContextFactory = Objects.requireNonNull(runContextFactory);
        this.flowInputOutput = Objects.requireNonNull(flowInputOutput);
    }

    /**
     * The values read by the task in its own properties, in the order they first appear, with their real value in the execution:
     * overrides from an earlier replay are left out. The tasks nested in a flowable task are not part of its own properties.
     */
    public List<ReplayValue> values(Flow flow, Execution execution, String taskRunId) throws InternalException {
        TaskRun taskRun = taskRun(execution, taskRunId);
        return values(flow, taskRun, variables(flow, execution, taskRun));
    }

    /**
     * @throws IllegalArgumentException if a key is not an input or an output read by the task in its own properties,
     *         or if an input with the value set no longer fits its type or validation rules
     */
    public List<TaskRunOverride> overrides(Flow flow, Execution execution, String taskRunId, Map<String, Object> requested) throws InternalException {
        TaskRun taskRun = taskRun(execution, taskRunId);
        Map<String, Object> variables = variables(flow, execution, taskRun);
        Map<String, ReplayValue> values = values(flow, taskRun, variables).stream()
            .collect(Collectors.toMap(ReplayValue::key, value -> value, (first, _) -> first));

        List<TaskRunOverride> overrides = new ArrayList<>(requested.size());
        for (Map.Entry<String, Object> entry : requested.entrySet()) {
            VariablePath path = VariablePath.parse(entry.getKey());
            String key = path.toString();
            ReplayValue value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException(
                    "Cannot override '%s': the task of task run '%s' does not read it in its own properties.".formatted(key, taskRunId)
                );
            }
            if (!value.kind().isOverridable()) {
                throw new IllegalArgumentException(
                    "Cannot override '%s': only inputs and outputs can be overridden, and this value is a %s.".formatted(key, value.kind().label())
                );
            }
            Object override = value.kind() == Kind.INPUT ? inputValue(flow, execution, variables, path, entry.getValue()) : entry.getValue();
            if (!Objects.equals(value.value(), override)) {
                overrides.add(new TaskRunOverride(key, override, value.value()));
            }
        }
        return overrides;
    }

    private List<ReplayValue> values(Flow flow, TaskRun taskRun, Map<String, Object> variables) throws InternalException {
        List<Input<?>> inputs = flow.resolvableInputs();
        return references(flow.findTaskByTaskId(taskRun.getTaskId())).stream()
            .map(reference -> reference instanceof VariablePath path ? valueOf(path, variables, inputs) : new ReplayValue(reference.toString(), Kind.SECRET, null, null))
            .toList();
    }

    private TaskRun taskRun(Execution execution, String taskRunId) {
        return execution.findTaskRunByTaskRunIdIfPresent(taskRunId).orElseThrow(
            () -> new IllegalArgumentException(
                "Task run '%s' does not exist in execution '%s'.".formatted(taskRunId, execution.getId())
            )
        );
    }

    private Map<String, Object> variables(Flow flow, Execution execution, TaskRun taskRun) throws InternalException {
        return runContextFactory.of(flow, flow.findTaskByTaskId(taskRun.getTaskId()), execution, taskRun.withOverrides(null), false).getVariables();
    }

    private Object inputValue(Flow flow, Execution execution, Map<String, Object> variables, VariablePath path, Object value) {
        Optional<Input<?>> declared = inputOf(path, flow.resolvableInputs());
        if (declared.isEmpty()) {
            return value;
        }
        Input<?> input = declared.get();
        List<Object> inside = path.segments().subList(1 + input.getId().split("\\.").length, path.size());
        VariablePath whole = new VariablePath(path.segments().subList(0, path.size() - inside.size()));
        VariablePath leaf = new VariablePath(Stream.concat(Stream.of("value"), inside.stream()).toList());
        try {
            Object parsed = flowInputOutput.parseInputValue(execution, input, whole.resolve(path.set(variables, value)).orElse(null));
            // as JSON, so that it compares with and is stored like the values read from the stored execution
            Object normalized = JacksonMapper.toObject(JacksonMapper.ofJson().writeValueAsString(parsed));
            return leaf.resolve(Collections.singletonMap("value", normalized)).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot override '%s': %s".formatted(path, e.getMessage()), e);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot override '%s': the value cannot be written as JSON.".formatted(path), e);
        }
    }

    private ReplayValue valueOf(VariablePath path, Map<String, Object> variables, List<Input<?>> inputs) {
        Object value = path.resolve(variables).orElse(null);
        Kind kind = ROOT_KINDS.get(path.root());
        Optional<Input<?>> input = kind == Kind.INPUT ? inputOf(path, inputs) : Optional.empty();
        Type inputType = input.filter(it -> it.getId().equals(inputPath(path))).map(Input::getType).orElse(null);

        if (value instanceof EncryptedString || input.filter(it -> Type.SECRET == it.getType()).isPresent()) {
            return new ReplayValue(path.toString(), Kind.SECRET, inputType, null);
        }
        if ((value instanceof String string && string.startsWith(FILE_URI_PREFIX)) || input.filter(it -> Type.FILE == it.getType()).isPresent()) {
            kind = Kind.FILE;
        }
        return new ReplayValue(path.toString(), kind, inputType, value);
    }

    private static Optional<Input<?>> inputOf(VariablePath path, List<Input<?>> inputs) {
        String inputPath = inputPath(path);
        return inputs.stream()
            .filter(input -> inputPath.equals(input.getId()) || inputPath.startsWith(input.getId() + "."))
            .findFirst();
    }

    private static String inputPath(VariablePath path) {
        return path.segments().stream().skip(1).map(Object::toString).collect(Collectors.joining("."));
    }

    private List<Object> references(Task task) {
        Set<String> childTaskIds = task instanceof FlowableTask<?> flowable
            ? flowable.allChildTasks().stream().map(Task::getId).collect(Collectors.toSet())
            : Set.of();

        ReferenceCollector collector = new ReferenceCollector();
        collectTemplates(JacksonMapper.toMap(task), childTaskIds).forEach(template -> parse(template).ifPresent(collector::visit));
        return collector.references();
    }

    private static List<String> collectTemplates(Object value, Set<String> childTaskIds) {
        List<String> templates = new ArrayList<>();
        switch (value) {
            case String string when PebbleUtil.containsOpeningBlockDelimiter(string) -> templates.add(string);
            case Map<?, ?> map when !isChildTask(map, childTaskIds) -> map.values().forEach(child -> templates.addAll(collectTemplates(child, childTaskIds)));
            case List<?> list -> list.forEach(child -> templates.addAll(collectTemplates(child, childTaskIds)));
            default -> {
            }
        }
        return templates;
    }

    private static boolean isChildTask(Map<?, ?> map, Set<String> childTaskIds) {
        return map.containsKey("type") && map.get("id") instanceof String id && childTaskIds.contains(id);
    }

    private Optional<RootNode> parse(String template) {
        ExtensionRegistry registry = pebbleEngine.getExtensionRegistry();
        try {
            LexerImpl lexer = new LexerImpl(pebbleEngine.getSyntax(), registry.getUnaryOperators().values(), registry.getBinaryOperators().values());
            ParserImpl parser = new ParserImpl(registry.getUnaryOperators(), registry.getBinaryOperators(), registry.getTokenParsers(), new ParserOptions());
            return Optional.of(parser.parse(lexer.tokenize(new StringReader(template), "replay-override")));
        } catch (PebbleException e) {
            // an invalid expression fails the task itself, it has nothing to list here
            log.debug("Unable to parse '{}' to list the values it reads", template, e);
            return Optional.empty();
        }
    }

    public enum Kind {
        INPUT,
        OUTPUT,
        SECRET,
        FILE,
        FLOW_VARIABLE,
        TRIGGER;

        public boolean isOverridable() {
            return this == INPUT || this == OUTPUT;
        }

        private String label() {
            return name().toLowerCase().replace('_', ' ');
        }
    }

    /**
     * @param key the variable path, or the {@code secret('KEY')} call for a secret read by the task
     * @param inputType the declared type when the key is a flow input itself, {@code null} otherwise
     * @param value the value in the execution, {@code null} for a secret
     */
    public record ReplayValue(String key, Kind kind, @Nullable Type inputType, Object value) {
    }

    private static final class ReferenceCollector {
        private final Map<String, Object> references = new LinkedHashMap<>();

        List<Object> references() {
            return List.copyOf(references.values());
        }

        void visit(Node node) {
            switch (node) {
                case RootNode root -> visit(root.getBody());
                case BodyNode body -> body.getChildren().forEach(this::visit);
                case PrintNode print -> visit(print.getExpression());
                case SetNode set -> visit(set.getValue());
                case ForNode loop -> {
                    visit(loop.getIterable());
                    visit(loop.getBody());
                    if (loop.getElseBody() != null) {
                        visit(loop.getElseBody());
                    }
                }
                case IfNode condition -> {
                    condition.getConditionsWithBodies().forEach(pair ->
                    {
                        visit(pair.getLeft());
                        visit(pair.getRight());
                    });
                    if (condition.getElseBody() != null) {
                        visit(condition.getElseBody());
                    }
                }
                default -> {
                }
            }
        }

        void visit(Expression<?> expression) {
            Optional<List<Object>> path = staticPath(expression);
            if (path.isPresent()) {
                if (path.get().size() > 1 && ROOT_KINDS.containsKey((String) path.get().getFirst())) {
                    VariablePath variablePath = new VariablePath(path.get());
                    references.putIfAbsent(variablePath.toString(), variablePath);
                }
                return;
            }

            switch (expression) {
                case GetAttributeExpression attribute -> {
                    visit(attribute.getNode());
                    visit(attribute.getAttributeNameExpression());
                    visit(attribute.getArgumentsNode());
                }
                case FunctionOrMacroInvocationExpression function -> {
                    if (
                        SecretFunction.NAME.equals(function.getFunctionName()) && !function.getArguments().getPositionalArgs().isEmpty()
                            && function.getArguments().getPositionalArgs().getFirst().getValueExpression() instanceof LiteralStringExpression secret
                    ) {
                        String key = "secret('%s')".formatted(secret.getValue());
                        references.putIfAbsent(key, key);
                    }
                    visit(function.getArguments());
                }
                case FilterInvocationExpression filter -> visit(filter.getArgs());
                case BinaryExpression<?> binary -> {
                    visit(binary.getLeftExpression());
                    visit(binary.getRightExpression());
                }
                case UnaryExpression unary -> visit(unary.getChildExpression());
                case TernaryExpression ternary -> {
                    visit(ternary.getExpression1());
                    visit(ternary.getExpression2());
                    visit(ternary.getExpression3());
                }
                case ArrayExpression array -> array.getValues().forEach(this::visit);
                case MapExpression map -> map.getEntries().forEach((key, value) ->
                {
                    visit(key);
                    visit(value);
                });
                case null, default -> {
                }
            }
        }

        private void visit(ArgumentsNode arguments) {
            if (arguments == null) {
                return;
            }
            arguments.getPositionalArgs().forEach(argument -> visit(argument.getValueExpression()));
            arguments.getNamedArgs().forEach(argument -> visit(argument.getValueExpression()));
        }

        private static Optional<List<Object>> staticPath(Expression<?> expression) {
            if (expression instanceof ContextVariableExpression variable) {
                return Optional.of(List.of(variable.getName()));
            }
            if (expression instanceof GetAttributeExpression attribute && attribute.getArgumentsNode() == null) {
                Object segment = switch (attribute.getAttributeNameExpression()) {
                    case LiteralStringExpression string -> string.getValue();
                    case LiteralLongExpression number -> number.getValue();
                    case LiteralIntegerExpression number -> number.getValue();
                    default -> null;
                };
                if (segment != null) {
                    return staticPath(attribute.getNode()).map(parent ->
                    {
                        List<Object> path = new ArrayList<>(parent);
                        path.add(segment);
                        return path;
                    });
                }
            }
            return Optional.empty();
        }
    }
}
