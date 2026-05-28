package io.kestra.core.services;

import java.util.*;
import java.util.stream.Collectors;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContextCache;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.utils.GraphUtils;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that builds a categorized map of available Pebble expressions for a given context.
 * <p>
 * Used by:
 * <ul>
 *   <li>The {@code POST /flows/expressions} endpoint — No-Code editor autocompletion</li>
 *   <li>AI Copilot prompt builders — format via {@code PebbleExpressionsFormatter.format(context.toDisplayNameMap())}</li>
 * </ul>
 * Expressions are returned <b>without</b> the {@code {{ }}} delimiters.
 */
@Singleton
@Slf4j
public class ExpressionContextService {

    /**
     * Execution context paths that are NOT available when rendering at execution-level
     * (no specific task/taskrun in scope, e.g. TestSuite assertions).
     */
    private static final Set<String> TASK_LEVEL_PATHS = Set.of(
        "task", "task.id", "task.type",
        "taskrun", "taskrun.attemptsCount", "taskrun.id", "taskrun.iteration",
        "taskrun.parentId", "taskrun.startDate", "taskrun.value",
        "parent", "parent.task", "parent.task.id", "parent.taskrun", "parent.taskrun.value",
        "parents",
        "item.index", "item.key", "item.parent", "item.parent.index", "item.parent.key",
        "item.parent.value", "item.parents", "item.value"
    );

    /** JSON Schema keywords that should not be treated as property names. */
    private static final Set<String> SCHEMA_KEYWORDS = Set.of(
        "$ref", "$defs", "$schema", "$id",
        "type", "required", "additionalProperties", "description",
        "title", "default", "enum", "const",
        "allOf", "anyOf", "oneOf", "not",
        "items", "minItems", "maxItems", "uniqueItems",
        "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
        "minLength", "maxLength", "pattern", "format",
        "minProperties", "maxProperties", "patternProperties",
        "if", "then", "else", "deprecated", "examples", "readOnly", "writeOnly"
    );

    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final PebbleExpressionService pebbleExpressionService;
    private final RunContextCache runContextCache;
    @SuppressWarnings("rawtypes")
    private final SecretService secretService;
    private final KVStoreService kvStoreService;
    private final StorageInterface storageInterface;
    private final NamespaceFactory namespaceFactory;

    @SuppressWarnings("rawtypes")
    @Inject
    public ExpressionContextService(
        JsonSchemaGenerator jsonSchemaGenerator,
        PebbleExpressionService pebbleExpressionService,
        RunContextCache runContextCache,
        SecretService secretService,
        KVStoreService kvStoreService,
        StorageInterface storageInterface,
        NamespaceFactory namespaceFactory) {
        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.pebbleExpressionService = pebbleExpressionService;
        this.runContextCache = runContextCache;
        this.secretService = secretService;
        this.kvStoreService = kvStoreService;
        this.storageInterface = storageInterface;
        this.namespaceFactory = namespaceFactory;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal expression context that does not require a flow.
     * Contains only {@link ExpressionCategory#FILTERS} and {@link ExpressionCategory#FUNCTIONS}.
     * <p>
     * Suitable as a fallback when no flow reference is available (e.g. TestSuite generation
     * when the referenced flow cannot be resolved).
     */
    public ExpressionContext buildGlobalExpressionContext() {
        return ExpressionContext.builder()
            .put(ExpressionCategory.FILTERS, buildFilters())
            .put(ExpressionCategory.FUNCTIONS, buildFunctions())
            .build();
    }

    /**
     * Builds the full flow-scoped expression context for flow generation and the
     * {@code POST /flows/expressions} No-Code editor endpoint.
     *
     * @param flow   the parsed flow
     * @param taskId optional task ID to scope task outputs to topological predecessors only
     */
    public ExpressionContext buildExpressionContext(Flow flow, @Nullable String taskId) {
        return buildExpressionContext(flow, taskId, Set.of());
    }

    /**
     * Same as {@link #buildExpressionContext(Flow, String)} but skips the given categories.
     * <p>
     * Used by EE to omit categories the caller is not permitted to read
     * ({@link ExpressionCategory#SECRETS}, {@link ExpressionCategory#KV_PAIRS},
     * {@link ExpressionCategory#NAMESPACE_FILES}) so secret/KV/file names are neither
     * fetched nor leaked. Excluded categories skip their backend call entirely.
     */
    public ExpressionContext buildExpressionContext(Flow flow, @Nullable String taskId, Set<ExpressionCategory> excludedCategories) {
        ExpressionContext.Builder builder = ExpressionContext.builder()
            .put(ExpressionCategory.TASK_OUTPUTS, buildTaskOutputs(flow, taskId))
            .put(ExpressionCategory.EXECUTION_CONTEXT, buildExecutionContext(flow))
            .put(ExpressionCategory.INPUTS, buildInputs(flow))
            .put(ExpressionCategory.VARIABLES, buildVariables(flow));

        if (!excludedCategories.contains(ExpressionCategory.SECRETS)) {
            builder.put(ExpressionCategory.SECRETS, buildSecrets(flow));
        }
        if (!excludedCategories.contains(ExpressionCategory.KV_PAIRS)) {
            builder.put(ExpressionCategory.KV_PAIRS, buildKvPairs(flow));
        }
        if (!excludedCategories.contains(ExpressionCategory.NAMESPACE_FILES)) {
            builder.put(ExpressionCategory.NAMESPACE_FILES, buildNamespaceFiles(flow));
        }

        return builder
            .put(ExpressionCategory.FILTERS, buildFilters())
            .put(ExpressionCategory.FUNCTIONS, buildFunctions())
            .build();
    }

    /**
     * Builds an App-specific expression context for App YAML generation.
     * <p>
     * Apps render Pebble via {@code DefaultAppContext.renderValue()} backed by {@code VariableRenderer}.
     * The available context is narrower than a full flow execution context:
     * <ul>
     *   <li>{@link ExpressionCategory#APP_CONTEXT}: app.*, params.*, dispatch/stream, namespace, flowId, flowRevision</li>
     *   <li>{@link ExpressionCategory#EXECUTION_CONTEXT}: execution.* subset (no task/taskrun/trigger/parents/item/envs/globals)</li>
     *   <li>{@link ExpressionCategory#FILTERS}: all filters</li>
     *   <li>{@link ExpressionCategory#FUNCTIONS}: all functions (secret() and kv() work via flow.namespace/tenantId)</li>
     * </ul>
     * <p>
     * Notably absent: inputs, vars, labels, task outputs, envs/globals, parents/item.
     * Secrets and KV pairs are accessible as functions ({@code secret('KEY')}, {@code kv('key')}),
     * not as context variables.
     */
    public ExpressionContext buildAppExpressionContext() {
        return ExpressionContext.builder()
            .put(ExpressionCategory.APP_CONTEXT, buildAppContextPaths())
            .put(ExpressionCategory.EXECUTION_CONTEXT, buildAppExecutionContext())
            .put(ExpressionCategory.FILTERS, buildFilters())
            .put(ExpressionCategory.FUNCTIONS, buildFunctions())
            .build();
    }

    private static List<String> buildAppContextPaths() {
        return List.of(
            "app.displayName", "app.error.message", "app.error.stacktrace",
            "app.id", "app.url",
            "dispatch", "flowId", "flowRevision", "namespace",
            "params.*", "stream"
        );
    }

    private static List<String> buildAppExecutionContext() {
        return List.of(
            "execution.id", "execution.isResuming", "execution.isRunning",
            "execution.isTerminated", "execution.outputs",
            "execution.startDate", "execution.state",
            "execution.task.id", "execution.task.type"
        );
    }

    /**
     * Builds an expression context for TestSuite assertion rendering.
     * <p>
     * Assertions are evaluated via {@code runContextFactory.of(flow, execution)} — flow+execution
     * scoped but without a specific task or taskrun. Task-level paths ({@code task.*},
     * {@code taskrun.*}, {@code parent.*}, {@code parents}, {@code item.*}) are excluded.
     * <p>
     * Task outputs (via {@code outputs.<taskId>.<prop>}) ARE available since
     * {@code taskOutputService.computeOutputs(execution)} is in scope.
     *
     * @param flow the flow referenced by the test suite's {@code flowId}+{@code namespace}
     */
    public ExpressionContext buildTestSuiteExpressionContext(Flow flow) {
        return ExpressionContext.builder()
            .put(ExpressionCategory.TASK_OUTPUTS, buildTaskOutputs(flow, null))
            .put(ExpressionCategory.EXECUTION_CONTEXT, buildExecutionContextForTestSuite(flow))
            .put(ExpressionCategory.INPUTS, buildInputs(flow))
            .put(ExpressionCategory.VARIABLES, buildVariables(flow))
            .put(ExpressionCategory.SECRETS, buildSecrets(flow))
            .put(ExpressionCategory.KV_PAIRS, buildKvPairs(flow))
            .put(ExpressionCategory.NAMESPACE_FILES, buildNamespaceFiles(flow))
            .put(ExpressionCategory.FILTERS, buildFilters())
            .put(ExpressionCategory.FUNCTIONS, buildFunctions())
            .build();
    }

    // -------------------------------------------------------------------------
    // Task outputs
    // -------------------------------------------------------------------------

    /**
     * Builds task output expressions by walking JSON schemas for each task's output class.
     * When {@code taskId} is provided, only topological predecessors (tasks guaranteed to
     * complete before {@code taskId} in the flow graph) are included.
     * <p>
     * Trigger outputs are merged under the {@code trigger.*} prefix.
     */
    private List<String> buildTaskOutputs(Flow flow, @Nullable String taskId) {
        List<Task> eligibleTasks = taskId != null
            ? getTopologicalPredecessors(flow, taskId)
            : flow.allTasksWithChilds();

        List<String> expressions = new ArrayList<>();
        for (Task task : eligibleTasks) {
            Map<String, Object> outputSchema = jsonSchemaGenerator.outputs(null, task.getClass());
            if (outputSchema.isEmpty() || !outputSchema.containsKey("properties")) {
                continue;
            }
            Map<String, Object> properties = extractProperties(outputSchema);
            if (properties != null) {
                expressions.addAll(flattenPropertyPaths(properties, outputSchema, taskOutputPrefix(task.getId())));
            }
        }

        // Trigger outputs merged under "trigger.*"
        if (flow.getTriggers() != null) {
            Set<String> triggerExpressions = new LinkedHashSet<>();
            for (AbstractTrigger trigger : flow.getTriggers()) {
                Map<String, Object> outputSchema = jsonSchemaGenerator.outputs(null, trigger.getClass());
                Map<String, Object> properties = extractProperties(outputSchema);
                if (properties != null) {
                    triggerExpressions.addAll(flattenPropertyPaths(properties, outputSchema, "trigger"));
                }
            }
            expressions.addAll(triggerExpressions);
        }

        Collections.sort(expressions);
        return expressions;
    }

    /**
     * Returns tasks that are guaranteed to complete before {@code targetTaskId} by walking
     * the flow DAG backward from the target node.
     * <p>
     * Unlike a definition-order cutoff, this correctly handles Parallel/Switch/ForEach:
     * tasks in sibling branches that do not precede {@code targetTaskId} are excluded.
     * Falls back to definition-order if graph construction fails.
     */
    private List<Task> getTopologicalPredecessors(Flow flow, String targetTaskId) {
        try {
            GraphCluster cluster = GraphUtils.of(flow, null);
            List<FlowGraph.Edge> edges = GraphUtils.edges(cluster);
            List<AbstractGraph> nodes = GraphUtils.nodes(cluster);

            // Find target node UID
            Optional<String> targetUid = nodes.stream()
                .filter(AbstractGraphTask.class::isInstance)
                .filter(n -> targetTaskId.equals(((AbstractGraphTask) n).getTask().getId()))
                .map(AbstractGraph::getUid)
                .findFirst();

            if (targetUid.isEmpty()) {
                return getTasksBeforeFallback(flow.allTasksWithChilds(), targetTaskId);
            }

            // Collect all transitive predecessor UIDs by walking edges backward
            Set<String> predecessorUids = new HashSet<>();
            collectBackwardEdges(edges, targetUid.get(), predecessorUids);

            // Map predecessor UIDs back to task IDs (excluding sentinel nodes)
            Set<String> predecessorTaskIds = nodes.stream()
                .filter(AbstractGraphTask.class::isInstance)
                .filter(n -> predecessorUids.contains(n.getUid()))
                .map(n -> ((AbstractGraphTask) n).getTask().getId())
                .collect(Collectors.toSet());

            return flow.allTasksWithChilds().stream()
                .filter(t -> predecessorTaskIds.contains(t.getId()))
                .toList();

        } catch (IllegalVariableEvaluationException e) {
            log.debug("Graph traversal failed for taskId={}, falling back to definition order: {}", targetTaskId, e.getMessage());
            return getTasksBeforeFallback(flow.allTasksWithChilds(), targetTaskId);
        }
    }

    /** Recursively collects UIDs of all transitive predecessors by following edges backward.
     * ERROR and FINALLY edges are excluded: those branches only execute on failure,
     * so their outputs are not available to tasks on the happy path. */
    private static void collectBackwardEdges(List<FlowGraph.Edge> edges, String targetUid, Set<String> visited) {
        for (FlowGraph.Edge edge : edges) {
            if (edge.getTarget().equals(targetUid)
                    && !isErrorOrFinallyEdge(edge)
                    && visited.add(edge.getSource())) {
                collectBackwardEdges(edges, edge.getSource(), visited);
            }
        }
    }

    private static boolean isErrorOrFinallyEdge(FlowGraph.Edge edge) {
        if (edge.getRelation() == null || edge.getRelation().getRelationType() == null) {
            return false;
        }
        RelationType type = edge.getRelation().getRelationType();
        return type == RelationType.ERROR || type == RelationType.FINALLY;
    }

    /** Definition-order fallback: tasks listed before {@code targetTaskId} in the flat task list. */
    private static List<Task> getTasksBeforeFallback(List<Task> allTasks, String targetTaskId) {
        List<Task> before = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getId().equals(targetTaskId)) {
                break;
            }
            before.add(task);
        }
        return before;
    }

    // -------------------------------------------------------------------------
    // Execution context
    // -------------------------------------------------------------------------

    /**
     * Full execution context: all structural paths from {@link RunVariables#EXECUTION_CONTEXT_PATHS},
     * plus flow-specific labels, envs, and globals.
     */
    private List<String> buildExecutionContext(Flow flow) {
        List<String> expressions = new ArrayList<>(RunVariables.EXECUTION_CONTEXT_PATHS);
        addFlowLabels(flow, expressions);
        addEnvsAndGlobals(expressions);
        Collections.sort(expressions);
        return expressions;
    }

    /**
     * Execution context for TestSuite assertions: same as flow context but without
     * task-level paths ({@code task.*}, {@code taskrun.*}, {@code parent.*}, {@code item.*}).
     */
    private List<String> buildExecutionContextForTestSuite(Flow flow) {
        List<String> expressions = RunVariables.EXECUTION_CONTEXT_PATHS.stream()
            .filter(path -> !TASK_LEVEL_PATHS.contains(path))
            .collect(Collectors.toCollection(ArrayList::new));
        addFlowLabels(flow, expressions);
        addEnvsAndGlobals(expressions);
        Collections.sort(expressions);
        return expressions;
    }

    private void addFlowLabels(Flow flow, List<String> expressions) {
        if (flow.getLabels() != null) {
            for (Label label : flow.getLabels()) {
                expressions.add("labels." + label.key());
            }
        }
    }

    private void addEnvsAndGlobals(List<String> expressions) {
        Map<String, String> envVars = runContextCache.getEnvVars();
        if (envVars != null) {
            for (String key : envVars.keySet()) {
                expressions.add("envs." + key);
            }
        }
        Map<?, ?> globalVars = runContextCache.getGlobalVars();
        if (globalVars != null) {
            for (Object key : globalVars.keySet()) {
                expressions.add("globals." + key);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Flow-specific categories
    // -------------------------------------------------------------------------

    private List<String> buildInputs(Flow flow) {
        if (flow.getInputs() == null || flow.getInputs().isEmpty()) {
            return List.of();
        }
        return flow.getInputs().stream()
            .map(input -> "inputs." + input.getId())
            .sorted()
            .toList();
    }

    private List<String> buildVariables(Flow flow) {
        if (flow.getVariables() == null || flow.getVariables().isEmpty()) {
            return List.of();
        }
        return flow.getVariables().keySet().stream()
            .map(key -> "vars." + key)
            .sorted()
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> buildSecrets(Flow flow) {
        try {
            Map<String, Set<String>> inherited = secretService.ownAndInheritedSecrets(
                flow.getTenantId(), flow.getNamespace()
            );
            return inherited.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .sorted()
                .map(key -> "secret('" + key + "')")
                .toList();
        } catch (Exception e) {
            log.warn("Could not fetch secrets for namespace {}: {}", flow.getNamespace(), e.getMessage());
            return List.of();
        }
    }

    private List<String> buildKvPairs(Flow flow) {
        try {
            List<KVEntry> entries = kvStoreService.get(flow.getTenantId(), flow.getNamespace()).list();
            return entries.stream()
                .map(entry -> "kv('" + entry.key() + "')")
                .sorted()
                .toList();
        } catch (Exception e) {
            log.warn("Could not fetch KV pairs for namespace {}: {}", flow.getNamespace(), e.getMessage());
            return List.of();
        }
    }

    private List<String> buildNamespaceFiles(Flow flow) {
        try {
            List<NamespaceFile> files = namespaceFactory.of(flow.getTenantId(), flow.getNamespace(), storageInterface).all();
            return files.stream()
                .map(file -> "read('" + file.path() + "')")
                .sorted()
                .toList();
        } catch (Exception e) {
            log.debug("Could not fetch namespace files for namespace {}: {}", flow.getNamespace(), e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Engine-level categories (filters + functions)
    // -------------------------------------------------------------------------

    /**
     * Returns filter names without any prefix — the {@link ExpressionCategory#FILTERS}
     * display name already notes "use as | filterName" so LLMs understand pipe syntax.
     */
    private List<String> buildFilters() {
        return pebbleExpressionService.filters();
    }

    /**
     * Returns functions as human-readable call signatures, e.g. {@code now()} or
     * {@code secret(key='MY_SECRET')}.
     */
    private List<String> buildFunctions() {
        return pebbleExpressionService.functions().stream()
            .map(PebbleFunction::toString)
            .sorted()
            .toList();
    }

    // -------------------------------------------------------------------------
    // Schema walking utilities
    // -------------------------------------------------------------------------

    /**
     * Returns the Pebble-safe output prefix for a task ID.
     * Simple alphanumeric+underscore IDs use dot notation: {@code outputs.taskId}.
     * IDs containing hyphens or other special characters use bracket notation:
     * {@code outputs['task-id']} — because Pebble parses {@code -} as subtraction
     * in dot-notation position, causing a parse error at runtime.
     */
    private static String taskOutputPrefix(String taskId) {
        if (taskId.matches("[A-Za-z][A-Za-z0-9_]*")) {
            return "outputs." + taskId;
        }
        return "outputs['" + taskId + "']";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractProperties(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveRef(String ref, Map<String, Object> rootSchema) {
        if (ref == null || !ref.startsWith("#/$defs/")) {
            return null;
        }
        String defName = ref.substring("#/$defs/".length());
        Object defs = rootSchema.get("$defs");
        if (defs instanceof Map<?, ?> defsMap) {
            Object resolved = defsMap.get(defName);
            if (resolved instanceof Map<?, ?> resolvedMap) {
                return (Map<String, Object>) resolvedMap;
            }
        }
        return null;
    }

    private static final int MAX_SCHEMA_DEPTH = 16;

    @SuppressWarnings("unchecked")
    private List<String> flattenPropertyPaths(Map<String, Object> properties, Map<String, Object> rootSchema, String prefix) {
        return flattenPropertyPaths(properties, rootSchema, prefix, 0);
    }

    @SuppressWarnings("unchecked")
    private List<String> flattenPropertyPaths(Map<String, Object> properties, Map<String, Object> rootSchema, String prefix, int depth) {
        if (depth >= MAX_SCHEMA_DEPTH) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (SCHEMA_KEYWORDS.contains(entry.getKey())) {
                continue;
            }
            String currentPath = prefix + "." + entry.getKey();
            paths.add(currentPath);

            if (entry.getValue() instanceof Map<?, ?> valueMap) {
                Map<String, Object> propDef = (Map<String, Object>) valueMap;
                Object refValue = propDef.get("$ref");
                if (refValue instanceof String refStr) {
                    Map<String, Object> resolved = resolveRef(refStr, rootSchema);
                    if (resolved != null) {
                        Map<String, Object> nestedProps = extractProperties(resolved);
                        if (nestedProps != null) {
                            paths.addAll(flattenPropertyPaths(nestedProps, rootSchema, currentPath, depth + 1));
                        }
                    }
                    continue;
                }
                Map<String, Object> nested = extractProperties(propDef);
                if (nested != null) {
                    paths.addAll(flattenPropertyPaths(nested, rootSchema, currentPath, depth + 1));
                }
            }
        }
        return paths;
    }
}
