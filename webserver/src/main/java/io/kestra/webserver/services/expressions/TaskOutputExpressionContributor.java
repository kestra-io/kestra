package io.kestra.webserver.services.expressions;

import java.util.*;
import java.util.stream.Collectors;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;
import io.kestra.core.utils.GraphUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds task output expressions by walking JSON schemas for each task's output class.
 * When {@code taskId} is provided, only topological predecessors (tasks guaranteed to
 * complete before {@code taskId} in the flow graph) are included.
 * <p>
 * Trigger outputs are merged under the {@code trigger.*} prefix.
 */
@Singleton
@Slf4j
public class TaskOutputExpressionContributor implements ExpressionContributor {
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

    private static final int MAX_SCHEMA_DEPTH = 16;

    private final JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    public TaskOutputExpressionContributor(JsonSchemaGenerator jsonSchemaGenerator) {
        this.jsonSchemaGenerator = jsonSchemaGenerator;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.TASK_OUTPUTS;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        String taskId = request.taskId();
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

    /**
     * Recursively collects UIDs of all transitive predecessors by following edges backward.
     * ERROR and FINALLY edges are excluded: those branches only execute on failure,
     * so their outputs are not available to tasks on the happy path.
     */
    private static void collectBackwardEdges(List<FlowGraph.Edge> edges, String targetUid, Set<String> visited) {
        for (FlowGraph.Edge edge : edges) {
            if (
                edge.getTarget().equals(targetUid)
                    && !isErrorOrFinallyEdge(edge)
                    && visited.add(edge.getSource())
            ) {
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
