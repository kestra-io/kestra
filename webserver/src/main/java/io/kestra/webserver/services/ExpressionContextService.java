package io.kestra.webserver.services;

import java.util.*;
import java.util.stream.Collectors;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContextCache;
import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVEntry;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that builds a categorized map of available Pebble expressions for a given flow.
 * <p>
 * This is used by the No-Code editor sidebar to provide autocompletion options,
 * and returns expressions <b>without</b> the {@code {{ }}} delimiters.
 */
@Singleton
@Slf4j
public class ExpressionContextService {

    private static final String CATEGORY_TASK_OUTPUTS = "Task Outputs";
    private static final String CATEGORY_EXECUTION_CONTEXT = "Execution Context";
    private static final String CATEGORY_INPUTS = "Inputs";
    private static final String CATEGORY_VARIABLES = "Variables";
    private static final String CATEGORY_SECRETS = "Secrets";
    private static final String CATEGORY_KV_PAIRS = "KV Pairs";
    private static final String CATEGORY_NAMESPACE_FILES = "Namespace Files";
    private static final String CATEGORY_FILTERS = "Filters";
    private static final String CATEGORY_OTHER = "Other";

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

    /**
     * Builds a categorized map of expression strings available for the given flow.
     *
     * @param flow   the parsed flow.
     * @param taskId optional task ID to scope outputs to prior tasks only.
     * @return a map from category name to sorted list of expression strings.
     */
    public Map<String, List<String>> buildExpressionContext(Flow flow, @Nullable String taskId) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        result.put(CATEGORY_TASK_OUTPUTS, buildTaskOutputs(flow, taskId));
        result.put(CATEGORY_EXECUTION_CONTEXT, buildExecutionContext(flow));
        result.put(CATEGORY_INPUTS, buildInputs(flow));
        result.put(CATEGORY_VARIABLES, buildVariables(flow));
        result.put(CATEGORY_SECRETS, buildSecrets(flow));
        result.put(CATEGORY_KV_PAIRS, buildKvPairs(flow));
        result.put(CATEGORY_NAMESPACE_FILES, buildNamespaceFiles(flow));
        result.put(CATEGORY_FILTERS, buildFilters());
        result.put(CATEGORY_OTHER, buildOther(flow));

        return result;
    }

    /**
     * Builds task output expressions by walking JSON schemas for each task's output class.
     * When {@code taskId} is provided, only tasks that execute <b>before</b> it are included.
     * <p>
     * Trigger outputs are merged under the {@code trigger.*} prefix since all triggers share
     * a single context map at runtime. If multiple triggers define overlapping output names,
     * all are included and deduplicated.
     */
    private List<String> buildTaskOutputs(Flow flow, @Nullable String taskId) {
        List<Task> allTasks = flow.allTasksWithChilds();
        List<Task> eligibleTasks;

        if (taskId != null) {
            eligibleTasks = getTasksBefore(allTasks, taskId);
        } else {
            eligibleTasks = allTasks;
        }

        List<String> expressions = new ArrayList<>();
        for (Task task : eligibleTasks) {
            Map<String, Object> outputSchema = jsonSchemaGenerator.outputs(null, task.getClass());
            if (outputSchema.isEmpty() || !outputSchema.containsKey("properties")) {
                continue;
            }

            Map<String, Object> properties = extractProperties(outputSchema);
            if (properties != null) {
                expressions.addAll(flattenPropertyPaths(properties, outputSchema, "outputs." + task.getId()));
            }
        }

        // Trigger outputs: merged under "trigger.*" (no trigger ID in the path)
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
     * Returns tasks from the ordered list that appear before the task with the given ID.
     */
    private List<Task> getTasksBefore(List<Task> allTasks, String taskId) {
        List<Task> before = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getId().equals(taskId)) {
                break;
            }
            before.add(task);
        }
        return before;
    }

    /**
     * Extracts the "properties" map from a JSON schema, handling nested structures.
     */
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

    /**
     * Resolves a {@code $ref} pointer against the schema's {@code $defs} section.
     *
     * @param ref        the $ref string, e.g. {@code "#/$defs/MyType"}
     * @param rootSchema the root schema map containing {@code $defs}
     * @return the resolved schema map, or {@code null} if not resolvable
     */
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

    /**
     * Recursively flattens a JSON schema properties map into dot-separated expression paths.
     * Both intermediate and leaf paths are included. Schema keywords ({@code $ref}, {@code type}, etc.)
     * are excluded. {@code $ref} pointers are followed when they reference {@code $defs} in the root schema.
     */
    @SuppressWarnings("unchecked")
    private List<String> flattenPropertyPaths(Map<String, Object> properties, Map<String, Object> rootSchema, String prefix) {
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (SCHEMA_KEYWORDS.contains(entry.getKey())) {
                continue;
            }

            String currentPath = prefix + "." + entry.getKey();
            paths.add(currentPath);

            if (entry.getValue() instanceof Map<?, ?> valueMap) {
                Map<String, Object> propDef = (Map<String, Object>) valueMap;

                // Follow $ref if present
                Object refValue = propDef.get("$ref");
                if (refValue instanceof String refStr) {
                    Map<String, Object> resolved = resolveRef(refStr, rootSchema);
                    if (resolved != null) {
                        Map<String, Object> nestedProps = extractProperties(resolved);
                        if (nestedProps != null) {
                            paths.addAll(flattenPropertyPaths(nestedProps, rootSchema, currentPath));
                        }
                    }
                    continue;
                }

                // Inline nested properties
                Map<String, Object> nested = extractProperties(propDef);
                if (nested != null) {
                    paths.addAll(flattenPropertyPaths(nested, rootSchema, currentPath));
                }
            }
        }
        return paths;
    }

    /**
     * Builds the execution context expressions — flow, execution, task, taskrun, parent, labels, etc.
     */
    private List<String> buildExecutionContext(Flow flow) {
        List<String> expressions = new ArrayList<>();

        expressions.add("flow.id");
        expressions.add("flow.namespace");
        if (flow.getRevision() != null) {
            expressions.add("flow.revision");
        }
        if (flow.getTenantId() != null) {
            expressions.add("flow.tenantId");
        }

        expressions.add("execution.id");
        expressions.add("execution.startDate");
        expressions.add("execution.state");
        expressions.add("execution.originalId");

        expressions.add("task.id");
        expressions.add("task.type");

        expressions.add("taskrun.id");
        expressions.add("taskrun.startDate");
        expressions.add("taskrun.attemptsCount");
        expressions.add("taskrun.parentId");
        expressions.add("taskrun.value");
        expressions.add("taskrun.iteration");

        expressions.add("parent.task.id");
        expressions.add("parent.taskrun.value");
        expressions.add("parents");

        if (flow.getLabels() != null) {
            for (Label label : flow.getLabels()) {
                expressions.add("labels." + label.key());
            }
        }

        Collections.sort(expressions);
        return expressions;
    }

    /**
     * Builds input expressions from the flow's declared inputs.
     */
    private List<String> buildInputs(Flow flow) {
        if (flow.getInputs() == null || flow.getInputs().isEmpty()) {
            return List.of();
        }

        return flow.getInputs().stream()
            .map(input -> "inputs." + input.getId())
            .sorted()
            .toList();
    }

    /**
     * Builds variable expressions from the flow's declared variables map.
     */
    private List<String> buildVariables(Flow flow) {
        if (flow.getVariables() == null || flow.getVariables().isEmpty()) {
            return List.of();
        }

        return flow.getVariables().keySet().stream()
            .map(key -> "vars." + key)
            .sorted()
            .toList();
    }

    /**
     * Builds secret expressions by looking up available secret keys for the flow's namespace.
     */
    private List<String> buildSecrets(Flow flow) {
        try {
            Map<String, Set<String>> inherited = secretService.inheritedSecrets(
                flow.getTenantId(), flow.getNamespace()
            );
            return inherited.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .sorted()
                .map(key -> "secret('" + key + "')")
                .toList();
        } catch (Exception e) {
            log.debug("Could not fetch secrets for namespace {}: {}", flow.getNamespace(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds KV pair expressions by listing keys from the namespace KV store.
     */
    private List<String> buildKvPairs(Flow flow) {
        try {
            List<KVEntry> entries = kvStoreService.get(flow.getTenantId(), flow.getNamespace()).list();
            return entries.stream()
                .map(entry -> "kv('" + entry.key() + "')")
                .sorted()
                .toList();
        } catch (Exception e) {
            log.debug("Could not fetch KV pairs for namespace {}: {}", flow.getNamespace(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds namespace file path expressions by listing files in the flow's namespace.
     */
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

    /**
     * Builds filter expressions from the Pebble engine.
     */
    private List<String> buildFilters() {
        return pebbleExpressionService.filters().stream()
            .map(filter -> "| " + filter)
            .toList();
    }

    /**
     * Builds the "Other" category — functions, envs, globals, and kestra configuration variables.
     */
    private List<String> buildOther(Flow flow) {
        List<String> expressions = new ArrayList<>();

        for (PebbleFunction fn : pebbleExpressionService.functions()) {
            expressions.add(formatFunction(fn));
        }

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

        expressions.add("kestra.environment");
        expressions.add("kestra.url");

        Collections.sort(expressions);
        return expressions;
    }

    /**
     * Formats a PebbleFunction as {@code name(arg1, arg2)} using argument defaults when available.
     *
     * @param fn the pebble function.
     * @return the formatted function call string.
     */
    static String formatFunction(PebbleFunction fn) {
        if (fn.arguments().isEmpty()) {
            return fn.name() + "()";
        }

        String args = fn.arguments().stream()
            .map(arg -> arg.defaultValue() != null ? arg.defaultValue() : arg.name())
            .collect(Collectors.joining(", "));

        return fn.name() + "(" + args + ")";
    }
}
