package io.kestra.core.services;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunOverride;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.runners.pebble.OverridableExpression;
import io.kestra.core.runners.pebble.PebbleExpressionPrinter;
import io.kestra.core.runners.pebble.functions.FileExistsFunction;
import io.kestra.core.runners.pebble.functions.FileSizeFunction;
import io.kestra.core.runners.pebble.functions.FileURIFunction;
import io.kestra.core.runners.pebble.functions.HttpFunction;
import io.kestra.core.runners.pebble.functions.IDFunction;
import io.kestra.core.runners.pebble.functions.IsFileEmptyFunction;
import io.kestra.core.runners.pebble.functions.KSUIDFunction;
import io.kestra.core.runners.pebble.functions.KvFunction;
import io.kestra.core.runners.pebble.functions.NanoIDFunction;
import io.kestra.core.runners.pebble.functions.NowFunction;
import io.kestra.core.runners.pebble.functions.RandomIntFunction;
import io.kestra.core.runners.pebble.functions.RandomPortFunction;
import io.kestra.core.runners.pebble.functions.ReadFileFunction;
import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.kestra.core.runners.pebble.functions.UUIDFunction;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.PebbleUtil;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.AbstractNodeVisitor;
import io.pebbletemplates.pebble.extension.ExtensionRegistry;
import io.pebbletemplates.pebble.lexer.LexerImpl;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.RootNode;
import io.pebbletemplates.pebble.node.expression.ContextVariableExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.node.expression.FunctionOrMacroInvocationExpression;
import io.pebbletemplates.pebble.parser.ParserImpl;
import io.pebbletemplates.pebble.parser.ParserOptions;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Lists the {@code {{ }}} expressions a task renders in its own properties, and turns the values a user sets for them on
 * replay into {@link TaskRunOverride}s keyed by the expression. Only a top-level expression that reads the execution can be
 * overridden: one inside a {@code {% %}} block, a constant, or one reading a secret, the KV store, a file, an external service or
 * a value that changes on every render stays as it is.
 */
@Slf4j
@Singleton
public class ReplayOverrideService {
    private static final String FILE_URI_PREFIX = "kestra://";
    private static final Set<String> NOT_RENDERED_PROPERTIES = Set.of("runIf", "description");
    private static final Set<String> SECRET_FUNCTIONS = Set.of(SecretFunction.NAME, KvFunction.NAME);
    private static final Set<String> FILE_FUNCTIONS = Set.of(
        ReadFileFunction.NAME, FileURIFunction.NAME, FileExistsFunction.NAME, FileSizeFunction.NAME, IsFileEmptyFunction.NAME
    );
    private static final Set<String> VOLATILE_FUNCTIONS = Set.of(
        NowFunction.NAME, UUIDFunction.NAME, IDFunction.NAME, KSUIDFunction.NAME, NanoIDFunction.NAME, RandomIntFunction.NAME, RandomPortFunction.NAME
    );

    private final PebbleEngine pebbleEngine;
    private final PebbleExpressionPrinter printer;
    private final RunContextFactory runContextFactory;
    private final VariableRenderer renderer;

    @Inject
    public ReplayOverrideService(ApplicationContext applicationContext, PebbleEngineFactory pebbleEngineFactory, RunContextFactory runContextFactory) {
        this.pebbleEngine = Objects.requireNonNull(pebbleEngineFactory).create();
        this.printer = new PebbleExpressionPrinter(pebbleEngine.getExtensionRegistry());
        this.runContextFactory = Objects.requireNonNull(runContextFactory);
        // created through the context so that a custom VariableRenderer is used, like SecureVariableRendererFactory does
        this.renderer = applicationContext.createBean(VariableRenderer.class);
        this.renderer.setPebbleEngine(pebbleEngineFactory.createWithMaskedFunctions(renderer, List.copyOf(SECRET_FUNCTIONS), List.of(HttpFunction.NAME)));
    }

    /**
     * The expressions of the task in its own properties, in the order they first appear, with what they rendered in the
     * execution: overrides from an earlier replay are left out. The tasks nested in a flowable task are not part of its own properties.
     */
    public List<ReplayValue> values(Flow flow, Execution execution, String taskRunId) throws InternalException {
        TaskRun taskRun = execution.findTaskRunByTaskRunIdIfPresent(taskRunId).orElseThrow(
            () -> new IllegalArgumentException(
                "Task run '%s' does not exist in execution '%s'.".formatted(taskRunId, execution.getId())
            )
        );
        Task task = flow.findTaskByTaskId(taskRun.getTaskId());
        RunContext runContext = runContextFactory.of(flow, task, execution, taskRun.withOverrides(null), false, renderer);

        Map<String, Kind> kinds = new LinkedHashMap<>();
        Set<String> inBlocks = new HashSet<>();
        templates(task).forEach(template -> parse(template).ifPresent(root -> collect(root, kinds, inBlocks)));
        // an override would only reach the top-level uses, so the ones inside a block would still render the real value
        inBlocks.forEach(expression -> kinds.computeIfPresent(expression, (key, kind) -> Kind.BLOCK));
        return kinds.entrySet().stream().map(entry -> valueOf(entry.getKey(), entry.getValue(), runContext)).toList();
    }

    /**
     * @throws IllegalArgumentException if a key is not an expression the task can have overridden in its own properties,
     *         or if no value differs from what the expression rendered
     */
    public List<TaskRunOverride> overrides(Flow flow, Execution execution, String taskRunId, Map<String, Object> requested) throws InternalException {
        Map<String, ReplayValue> values = values(flow, execution, taskRunId).stream()
            .collect(Collectors.toMap(ReplayValue::expression, value -> value));

        List<TaskRunOverride> overrides = new ArrayList<>(requested.size());
        for (Map.Entry<String, Object> entry : requested.entrySet()) {
            String key = parse("{{ %s }}".formatted(entry.getKey()))
                .flatMap(root -> root.getBody().getChildren().size() == 1 && root.getBody().getChildren().getFirst() instanceof PrintNode print ? printer.print(print.getExpression()) : Optional.empty())
                .orElse(entry.getKey());
            ReplayValue value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException(
                    "Cannot override '%s': the task of task run '%s' does not render it in its own properties.".formatted(key, taskRunId)
                );
            }
            if (value.kind() != Kind.EXPRESSION) {
                throw new IllegalArgumentException(
                    "Cannot override '%s': %s.".formatted(key, value.kind().reason())
                );
            }
            if (!Objects.equals(value.value(), entry.getValue())) {
                overrides.add(new TaskRunOverride(key, entry.getValue(), value.value()));
            }
        }
        if (overrides.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot replay task run '%s' with overrides: no value differs from what its expression rendered.".formatted(taskRunId)
            );
        }
        return overrides;
    }

    private void collect(RootNode root, Map<String, Kind> kinds, Set<String> inBlocks) {
        List<PrintNode> prints = OverridableExpression.overridablePrints(root);
        Set<PrintNode> overridable = Collections.newSetFromMap(new IdentityHashMap<>());
        overridable.addAll(prints);
        prints.forEach(print -> printer.print(print.getExpression()).ifPresent(expression -> kinds.putIfAbsent(expression, kindOf(print.getExpression()))));
        root.accept(new AbstractNodeVisitor(null) {
            @Override
            public void visit(PrintNode print) {
                if (!overridable.contains(print)) {
                    printer.print(print.getExpression()).ifPresent(expression -> {
                        kinds.putIfAbsent(expression, Kind.BLOCK);
                        inBlocks.add(expression);
                    });
                }
            }
        });
    }

    private static Kind kindOf(Expression<?> expression) {
        if (uses(expression, function -> SECRET_FUNCTIONS.contains(function.getFunctionName()))) {
            return Kind.SECRET;
        }
        if (uses(expression, function -> HttpFunction.NAME.equals(function.getFunctionName()))) {
            return Kind.CALL;
        }
        if (uses(expression, function -> FILE_FUNCTIONS.contains(function.getFunctionName()))) {
            return Kind.FILE;
        }
        if (uses(expression, function -> VOLATILE_FUNCTIONS.contains(function.getFunctionName()))) {
            return Kind.VOLATILE;
        }
        if (!readsContext(expression)) {
            return Kind.CONSTANT;
        }
        return Kind.EXPRESSION;
    }

    private static boolean uses(Expression<?> expression, Predicate<FunctionOrMacroInvocationExpression> function) {
        return expression instanceof FunctionOrMacroInvocationExpression invocation && function.test(invocation)
            || PebbleExpressionPrinter.children(expression).stream().anyMatch(child -> uses(child, function));
    }

    private static boolean readsContext(Expression<?> expression) {
        return expression instanceof ContextVariableExpression || expression instanceof FunctionOrMacroInvocationExpression
            || PebbleExpressionPrinter.children(expression).stream().anyMatch(ReplayOverrideService::readsContext);
    }

    private ReplayValue valueOf(String expression, Kind kind, RunContext runContext) {
        if (kind != Kind.EXPRESSION && kind != Kind.CONSTANT) {
            return new ReplayValue(expression, kind, null);
        }
        Object value;
        try {
            value = runContext.renderTyped("{{ %s }}".formatted(expression));
        } catch (IllegalVariableEvaluationException e) {
            // the task fails on such an expression too, which is a reason to replay it with a value
            value = null;
        }
        if (kind == Kind.EXPRESSION && value instanceof String string && string.startsWith(FILE_URI_PREFIX)) {
            kind = Kind.FILE;
        }
        return new ReplayValue(expression, kind, value);
    }

    private static List<String> templates(Task task) {
        Set<String> childTaskIds = task instanceof FlowableTask<?> flowable
            ? flowable.allChildTasks().stream().map(Task::getId).collect(Collectors.toSet())
            : Set.of();
        Map<String, Object> properties = new LinkedHashMap<>(JacksonMapper.toMap(task));
        properties.keySet().removeAll(NOT_RENDERED_PROPERTIES);
        return collectTemplates(properties, childTaskIds);
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
            log.debug("Unable to parse '{}' to list the expressions it renders", template, e);
            return Optional.empty();
        }
    }

    public enum Kind {
        EXPRESSION("it can be overridden"),
        BLOCK("it is inside a loop, a set or a macro, where it can read variables that only exist there"),
        CONSTANT("it reads nothing from the execution, so it is part of the task's definition"),
        SECRET("it reads a secret or the KV store"),
        FILE("it reads or renders a file"),
        CALL("it calls an external service, which rendering it would call again"),
        VOLATILE("it renders a different value every time, such as the current time or a random id");

        private final String reason;

        Kind(String reason) {
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }

    /**
     * @param expression the expression as Pebble parses it, with its spacing and quotes normalized
     * @param value what it rendered in the execution, {@code null} for a kind that is not rendered here or for one that failed to render
     */
    public record ReplayValue(String expression, Kind kind, Object value) {
    }
}
