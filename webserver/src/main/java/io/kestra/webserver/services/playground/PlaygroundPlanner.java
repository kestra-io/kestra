package io.kestra.webserver.services.playground;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.GraphUtils;

/**
 * Decides what a Playground run reuses from the run it builds on, where it replays from, and where it stops.
 */
public final class PlaygroundPlanner {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private PlaygroundPlanner() {
    }

    /**
     * @param buildOn the run to build on, with {@code buildOnFlow} the revision it ran; both null to start fresh
     * @param taskId the task to run up to or from, null to run the whole flow
     */
    public static PlaygroundPlan plan(Flow flow, String taskId, PlaygroundRunMode mode, Execution buildOn, Flow buildOnFlow) {
        List<String> breakpoints = taskId != null && PlaygroundRunMode.UP_TO == mode ? nextTaskIds(flow, taskId) : List.of();

        if (taskId == null) {
            return PlaygroundPlan.fresh(PlaygroundFreshReason.WHOLE_FLOW, breakpoints);
        }
        if (buildOn == null || buildOnFlow == null) {
            return PlaygroundPlan.fresh(PlaygroundFreshReason.NO_PREVIOUS_RUN, breakpoints);
        }
        Optional<PlaygroundFreshReason> flowChange = flowLevelChange(buildOnFlow, flow);
        if (flowChange.isPresent()) {
            return PlaygroundPlan.fresh(flowChange.get(), breakpoints);
        }

        List<String> order = taskIdsInOrder(flow);
        if (!order.contains(taskId)) {
            throw new IllegalArgumentException("Task '%s' does not exist in revision %s of flow '%s'.".formatted(taskId, flow.getRevision(), flow.getId()));
        }

        List<String> before = order.subList(0, order.indexOf(taskId));
        String replayPoint = before.stream()
            .filter(id -> !sameDefinition(buildOnFlow.findTaskByTaskIdOrNull(id), flow.findTaskByTaskIdOrNull(id)))
            .findFirst()
            .orElse(taskId);

        List<TaskRun> replayTaskRuns = buildOn.findTaskRunsByTaskId(replayPoint);
        if (replayTaskRuns.isEmpty()) {
            return PlaygroundPlan.fresh(PlaygroundFreshReason.NOT_REACHED, breakpoints);
        }

        Set<String> ranTaskIds = buildOn.getTaskRunList().stream().map(TaskRun::getTaskId).collect(Collectors.toSet());
        List<String> reused = order.subList(0, order.indexOf(replayPoint)).stream().filter(ranTaskIds::contains).toList();

        return new PlaygroundPlan(buildOn.getId(), replayTaskRuns.getFirst().getId(), replayPoint, reused, breakpoints, PlaygroundFreshReason.NONE);
    }

    private static Optional<PlaygroundFreshReason> flowLevelChange(Flow previous, Flow current) {
        if (!sameTree(previous.getInputs(), current.getInputs())) {
            return Optional.of(PlaygroundFreshReason.INPUTS_CHANGED);
        }
        if (!sameTree(previous.getLabels(), current.getLabels())) {
            return Optional.of(PlaygroundFreshReason.LABELS_CHANGED);
        }
        if (!sameTree(previous.getVariables(), current.getVariables())) {
            return Optional.of(PlaygroundFreshReason.VARIABLES_CHANGED);
        }
        return Optional.empty();
    }

    private static boolean sameDefinition(Task previous, Task current) {
        return previous != null && sameTree(previous, current);
    }

    private static boolean sameTree(Object previous, Object current) {
        return Objects.equals(MAPPER.valueToTree(previous), MAPPER.valueToTree(current));
    }

    /** Task ids level by level from the start of the flow, without the error branches. */
    static List<String> taskIdsInOrder(Flow flow) {
        Walk walk = Walk.of(flow);
        LinkedHashSet<String> order = new LinkedHashSet<>();
        List<String> level = walk.nextTaskNodes(List.of(walk.rootUid()));
        int guard = walk.nodes().size() + 1;
        while (!level.isEmpty() && guard-- > 0) {
            level.forEach(uid -> order.add(walk.taskId(uid)));
            level = walk.nextTaskNodes(level);
        }
        return new ArrayList<>(order);
    }

    /** The tasks a run must stop before so that {@code taskId} is the last one to run; never an {@code afterExecution} task. */
    static List<String> nextTaskIds(Flow flow, String taskId) {
        Walk walk = Walk.of(flow);
        List<String> targets = walk.taskNodes().entrySet().stream()
            .filter(entry -> taskId.equals(entry.getValue().getTask().getId()))
            .map(Map.Entry::getKey)
            .toList();
        return walk.nextTaskNodes(targets).stream()
            .filter(uid -> AbstractGraph.BranchType.AFTER_EXECUTION != walk.taskNodes().get(uid).getBranchType())
            .map(walk::taskId)
            .distinct()
            .toList();
    }

    private record Walk(String rootUid, List<FlowGraph.Edge> edges, Map<String, AbstractGraph> nodes, Map<String, AbstractGraphTask> taskNodes) {
        static Walk of(Flow flow) {
            GraphCluster graph;
            try {
                graph = GraphUtils.of(flow, null);
            } catch (IllegalVariableEvaluationException e) {
                throw new KestraRuntimeException("Cannot build the graph of flow '%s'.".formatted(flow.getId()), e);
            }
            List<AbstractGraph> nodes = GraphUtils.nodes(graph);
            return new Walk(
                graph.getRoot().getUid(),
                GraphUtils.edges(graph).stream()
                    .filter(edge -> edge.getRelation() == null || RelationType.ERROR != edge.getRelation().getRelationType())
                    .toList(),
                nodes.stream().collect(Collectors.toMap(AbstractGraph::getUid, node -> node, (a, b) -> a)),
                nodes.stream()
                    .filter(AbstractGraphTask.class::isInstance)
                    .map(AbstractGraphTask.class::cast)
                    .filter(node -> node.getTask() != null)
                    .collect(Collectors.toMap(AbstractGraph::getUid, node -> node, (a, b) -> a))
            );
        }

        String taskId(String uid) {
            return taskNodes.get(uid).getTask().getId();
        }

        /** Same walk as the topology's getNextTaskNodes: follow edges through structural nodes until task nodes are reached. */
        List<String> nextTaskNodes(List<String> fromUids) {
            List<String> current = fromUids;
            int guard = nodes.size() + 1;
            while (guard-- > 0) {
                Set<String> targets = new LinkedHashSet<>();
                for (String uid : current) {
                    edges.stream().filter(edge -> uid.equals(edge.getSource())).forEach(edge -> targets.add(edge.getTarget()));
                }
                if (targets.isEmpty()) {
                    return List.of();
                }
                List<String> tasks = targets.stream().filter(taskNodes::containsKey).toList();
                if (!tasks.isEmpty()) {
                    return tasks;
                }
                current = List.copyOf(targets);
            }
            return List.of();
        }
    }
}
