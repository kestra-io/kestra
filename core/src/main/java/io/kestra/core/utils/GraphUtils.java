package io.kestra.core.utils;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.plugin.core.flow.Dag;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphUtils {
    public static FlowGraph flowGraph(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        return GraphUtils.flowGraph(flow, execution, null);
    }

    public static FlowGraph flowGraph(Flow flow, Execution execution, List<Trigger> triggers) throws IllegalVariableEvaluationException {
        return FlowGraph.of(GraphUtils.of(flow, execution, triggers));
    }

    public static GraphCluster of(GraphCluster graph, Flow flow, Execution execution, List<Trigger> triggers) throws IllegalVariableEvaluationException {
        if (graph == null) {
            graph = new GraphCluster();
        }

        if (flow.getTriggers() != null) {
            GraphCluster triggersClusters = GraphUtils.triggers(graph, flow.getTriggers(), triggers);
            graph.addEdge(triggersClusters.getEnd(), graph.getRoot(), new Relation());
        }

        GraphUtils.sequential(
            graph,
            flow.getTasks(),
            flow.getErrors(),
            flow.getFinally(),
            null,
            execution
        );

        return graph;
    }

    public static GraphCluster of(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        return GraphUtils.of(flow, execution, null);
    }

    public static GraphCluster of(Flow flow, Execution execution, List<Trigger> triggers) throws IllegalVariableEvaluationException {
        return GraphUtils.of(new GraphCluster(), flow, execution, triggers);
    }

    public static GraphCluster triggers(GraphCluster graph, List<AbstractTrigger> triggersDeclarations, List<Trigger> triggers) throws IllegalVariableEvaluationException {
        GraphCluster triggerCluster = new GraphCluster("Triggers");

        graph.addNode(triggerCluster);

        Map<String, Trigger> triggersById = Optional.ofNullable(triggers)
            .map(Collection::stream)
            .map(s -> s.collect(Collectors.toMap(
                Trigger::getTriggerId,
                Function.identity(),
                (a, b) -> a.getNamespace().length() <= b.getNamespace().length() ? a : b
            )))
            .orElse(Collections.emptyMap());

        triggersDeclarations.forEach(trigger -> {
            GraphTrigger triggerNode = new GraphTrigger(trigger, triggersById.get(trigger.getId()));
            triggerCluster.addNode(triggerNode);
            triggerCluster.addEdge(triggerCluster.getRoot(), triggerNode, new Relation());
            triggerCluster.addEdge(triggerNode, triggerCluster.getEnd(), new Relation());
        });

        removeFinally(triggerCluster);

        return triggerCluster;
    }

    public static List<AbstractGraph> nodes(GraphCluster graphCluster) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> t instanceof GraphCluster cluster ? nodes(cluster).stream() : Stream.of(t))
            .distinct()
            .toList();
    }

    private static List<Triple<AbstractGraph, AbstractGraph, Relation>> rawEdges(GraphCluster graphCluster) {
        return Stream.concat(
                graphCluster.getGraph().edges()
                    .stream()
                    .map(r -> Triple.of(r.getSource(), r.getTarget(), r.getValue())),
                graphCluster.getGraph().nodes()
                    .stream()
                    .flatMap(t -> t instanceof GraphCluster cluster ? rawEdges(cluster).stream() : Stream.of())
            )
            .toList();
    }

    public static List<FlowGraph.Edge> edges(GraphCluster graphCluster) {
        return rawEdges(graphCluster)
            .stream()
            .map(r -> new FlowGraph.Edge(r.getLeft().getUid(), r.getMiddle().getUid(), r.getRight()))
            .toList();
    }

    public static List<Pair<GraphCluster, List<String>>> clusters(GraphCluster graphCluster, List<String> parents) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> {

                if (t instanceof GraphCluster cluster) {
                    ArrayList<String> currentParents = new ArrayList<>(parents);
                    currentParents.add(cluster.getUid());

                    return Stream.concat(
                        Stream.of(Pair.of(cluster, parents)),
                        clusters(cluster, currentParents).stream()
                    );
                }

                return Stream.of();
            })
            .toList();
    }

    public static Set<AbstractGraph> successors(GraphCluster graphCluster, List<String> taskRunIds) {
        List<FlowGraph.Edge> edges = GraphUtils.edges(graphCluster);
        List<AbstractGraph> nodes = GraphUtils.nodes(graphCluster);

        List<AbstractGraph> selectedTaskRuns = nodes
            .stream()
            .filter(AbstractGraphTask.class::isInstance)
            .filter(task -> ((AbstractGraphTask) task).getTaskRun() != null && taskRunIds.contains(((AbstractGraphTask) task).getTaskRun().getId()))
            .toList();

        Set<String> edgeUuid = selectedTaskRuns
            .stream()
            .flatMap(task -> recursiveEdge(edges, task.getUid()).stream())
            .map(FlowGraph.Edge::getSource)
            .collect(Collectors.toSet());

        return nodes
            .stream()
            .filter(task -> edgeUuid.contains(task.getUid()))
            .collect(Collectors.toSet());
    }

    private static List<FlowGraph.Edge> recursiveEdge(List<FlowGraph.Edge> edges, String selectedUuid) {
        return edges
            .stream()
            .filter(edge -> edge.getSource().equals(selectedUuid))
            .flatMap(edge -> Stream.concat(
                Stream.of(edge),
                recursiveEdge(edges, edge.getTarget()).stream()
            ))
            .toList();
    }

    public static void sequential(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        iterate(graph, tasks, errors, _finally, parent, execution, RelationType.SEQUENTIAL);
    }

    public static void parallel(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        iterate(graph, tasks, errors, _finally, parent, execution, RelationType.PARALLEL);
    }

    public static void switchCase(
        GraphCluster graph,
        Map<String, List<Task>> tasks,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        for (Map.Entry<String, List<Task>> entry : tasks.entrySet()) {
            fillGraph(graph, entry.getValue(), RelationType.SEQUENTIAL, parent, execution, entry.getKey());
        }

        fillAlternativePaths(graph, errors, _finally, parent, execution, null);
    }

    public static void ifElse(
        GraphCluster graph,
        List<Task> then,
        List<Task> _else,
        List<Task> _finally,
        List<Task> errors,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        fillGraph(graph, then, RelationType.SEQUENTIAL, parent, execution, "then");
        if (_else != null) {
            fillGraph(graph, _else, RelationType.SEQUENTIAL, parent, execution, "else");
        }

        fillAlternativePaths(graph, errors, _finally, parent, execution, null);
    }

    public static void dag(
        GraphCluster graph,
        List<Dag.DagTask> tasks,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        fillGraphDag(graph, tasks, parent, execution);

        fillAlternativePaths(graph, errors, _finally, parent, execution, null);
    }

    private static void iterate(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution,
        RelationType relationType
    ) throws IllegalVariableEvaluationException {
        fillGraph(graph, tasks, relationType, parent, execution, null);

        fillAlternativePaths(graph, errors, _finally, parent, execution, null);
    }

    private static void fillAlternativePaths(
        GraphCluster graph,
        List<Task> errors,
        List<Task> _finally,
        TaskRun parent,
        Execution execution,
        String value
    ) throws IllegalVariableEvaluationException {
        // error cases
        if (errors != null && !errors.isEmpty()) {
            fillGraph(graph, errors, RelationType.ERROR, parent, execution, value);
        }

        // finally cases
        if (_finally != null && !_finally.isEmpty()) {
            fillGraph(graph, _finally, RelationType.FINALLY, parent, execution, value);
        } else {
            removeFinally(graph);
        }
    }

    private static void removeFinally(GraphCluster graph) {
        // we don't have finally case, so we remove the node, and link all previous link to finally to the end
        graph.getGraph().edges()
            .forEach(edge -> {
                if (edge.getSource() instanceof GraphClusterFinally && edge.getTarget() instanceof GraphClusterEnd) {
                    graph.getGraph().edges().remove(edge);
                }

                if (edge.getTarget() instanceof GraphClusterFinally) {
                    graph.getGraph().edges().remove(edge);
                    graph.addEdge(edge.getSource(), graph.getEnd(), edge.getValue());
                }
            });

        graph.getGraph().removeNode(graph.getFinally());
    }

    private static void fillGraph(
        GraphCluster graph,
        List<Task> tasks,
        RelationType relationType,
        TaskRun parent,
        Execution execution,
        String value
    ) throws IllegalVariableEvaluationException {
        Iterator<Task> iterator = tasks.iterator();
        AbstractGraph previous;

        previous = Optional.<AbstractGraph>ofNullable(graph.getTaskNode()).orElse(graph.getRoot());
        if (relationType == RelationType.FINALLY) {
            previous = graph.getFinally();

            graph.getGraph().removeEdge(graph.getFinally(), graph.getEnd());
        }

        boolean isFirst = true;
        while (iterator.hasNext()) {
            Task currentTask = iterator.next();
            for (TaskRun currentTaskRun : findTaskRuns(currentTask, execution, parent)) {
                AbstractGraph currentGraph;
                List<String> parentValues = null;

                // we use the graph relation type by default but we change it to pass relation for case below
                RelationType newRelation = graph.getRelationType();
                if (relationType == RelationType.ERROR) {
                    newRelation = relationType;
                } else if ((!isFirst && relationType != RelationType.PARALLEL && graph.getRelationType() != RelationType.DYNAMIC)) {
                    newRelation = relationType;
                }

                Relation relation = new Relation(
                    newRelation,
                    currentTaskRun == null ? value : currentTaskRun.getValue()
                );

                if (execution != null && currentTaskRun != null) {
                    parentValues = execution.findParentsValues(currentTaskRun, true);
                }

                // detect kids
                if (currentTask instanceof FlowableTask<?> flowableTask) {
                    currentGraph = flowableTask.tasksTree(execution, currentTaskRun, parentValues);
                } else if (currentTask instanceof ExecutableTask<?> subflowTask) {
                    currentGraph = new SubflowGraphTask(subflowTask, currentTaskRun, parentValues, relationType);
                } else {
                    currentGraph = new GraphTask(currentTask, currentTaskRun, parentValues, relationType);
                }

                // add the node
                graph.addNode(currentGraph);

                if (relationType == RelationType.ERROR || relationType == RelationType.FINALLY) {
                    currentGraph.updateWithChildren(AbstractGraph.BranchType.valueOf(relationType.name()));
                }

                if (relationType == RelationType.ERROR) {
                    if (isFirst) {
                        previous = graph.getRoot();
                    }
                }

                if (previous != null) {
                    if (previous instanceof GraphCluster previousCluster && previousCluster.getEnd() != null) {
                        graph.addEdge(previousCluster.getEnd(), toEdgeTarget(currentGraph), relation);
                    } else {
                        graph.addEdge(previous, toEdgeTarget(currentGraph), relation);
                    }
                }

                // change previous for current one to link
                if (relationType != RelationType.PARALLEL) {
                    previous = currentGraph;
                }

                // link to end task
                if (GraphUtils.isAllLinkToEnd(relationType)) {
                    if (currentGraph instanceof GraphCluster && ((GraphCluster) currentGraph).getEnd() != null) {
                        graph.addEdge(
                            ((GraphCluster) currentGraph).getEnd(),
                            relationType == RelationType.FINALLY ? graph.getEnd() : graph.getFinally(),
                            new Relation()
                        );
                    } else {
                        graph.addEdge(
                            currentGraph,
                            relationType == RelationType.FINALLY ? graph.getEnd() : graph.getFinally(),
                            new Relation()
                        );
                    }
                }

                isFirst = false;

                if (!iterator.hasNext() && !isAllLinkToEnd(relationType)) {
                    graph.addEdge(
                        currentGraph instanceof GraphCluster ? ((GraphCluster) currentGraph).getEnd() : currentGraph,
                        relationType == RelationType.FINALLY ? graph.getEnd() : graph.getFinally(),
                        new Relation()
                    );
                }
            }
        }
    }

    private static AbstractGraph toEdgeTarget(AbstractGraph currentGraph) {
        return currentGraph instanceof GraphCluster ? ((GraphCluster) currentGraph).getRoot() : currentGraph;
    }

    private static void fillGraphDag(
        GraphCluster graph,
        List<Dag.DagTask> tasks,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        List<GraphTask> nodeTaskCreated = new ArrayList<>();
        List<String> nodeCreatedIds = new ArrayList<>();

        Set<String> dependencies = tasks
            .stream()
            .map(Dag.DagTask::getDependsOn)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        AbstractGraph previous;

        previous = Optional.<AbstractGraph>ofNullable(graph.getTaskNode()).orElse(graph.getRoot());

        while (nodeCreatedIds.size() < tasks.size()) {
            Iterator<Dag.DagTask> iterator = tasks.stream().filter(taskDepend ->
                // Check if the task has no dependencies OR all if its dependencies have been treated
                (taskDepend.getDependsOn() == null || new HashSet<>(nodeCreatedIds).containsAll(taskDepend.getDependsOn()))
                    // AND if the task has not been treated yet
                    && !nodeCreatedIds.contains(taskDepend.getTask().getId())).iterator();
            while (iterator.hasNext()) {
                Dag.DagTask currentTask = iterator.next();
                for (TaskRun currentTaskRun : findTaskRuns(currentTask.getTask(), execution, parent)) {
                    AbstractGraph currentGraph;
                    List<String> parentValues = null;

                    RelationType newRelation = RelationType.PARALLEL;

                    Relation relation = new Relation(
                        newRelation,
                        currentTaskRun == null ? null : currentTaskRun.getValue()
                    );

                    if (execution != null && currentTaskRun != null) {
                        parentValues = execution.findParentsValues(currentTaskRun, true);
                    }

                    // detect kids
                    if (currentTask.getTask() instanceof FlowableTask<?> flowableTask) {
                        currentGraph = flowableTask.tasksTree(execution, currentTaskRun, parentValues);
                    } else {
                        currentGraph = new GraphTask(currentTask.getTask(), currentTaskRun, parentValues, RelationType.SEQUENTIAL);
                    }

                    // add the node
                    graph.addNode(currentGraph);

                    // link to previous one
                    if (currentTask.getDependsOn() == null) {
                        graph.addEdge(
                            previous,
                            toEdgeTarget(currentGraph),
                            relation
                        );
                    } else {
                        for (String dependsOn : currentTask.getDependsOn()) {
                            GraphTask previousNode = nodeTaskCreated.stream().filter(node -> node.getTask().getId().equals(dependsOn)).findFirst().orElse(null);
                            if (previousNode != null && !((Task) previousNode.getTask()).isFlowable()) {
                                graph.addEdge(
                                    previousNode,
                                    toEdgeTarget(currentGraph),
                                    relation
                                );
                            } else {
                                graph.getGraph()
                                    .nodes()
                                    .stream()
                                    .filter(node -> node instanceof GraphCluster)
                                    .filter(node -> node.getUid().endsWith(dependsOn))
                                    .findFirst()
                                    .ifPresent(previousClusterNodeEnd -> graph.addEdge(
                                        ((GraphCluster) previousClusterNodeEnd).getEnd(),
                                        toEdgeTarget(currentGraph),
                                        relation
                                    ));
                            }
                        }
                    }
                    // link to last one if task isn't a dependency
                    if (!dependencies.contains(currentTask.getTask().getId())) {
                        if (currentTask.getTask() instanceof FlowableTask<?>) {
                            graph.addEdge(
                                ((GraphCluster) currentGraph).getEnd(),
                                graph.getFinally(),
                                new Relation()
                            );
                        } else {
                            graph.addEdge(
                                currentGraph,
                                graph.getFinally(),
                                new Relation()
                            );
                        }
                    }

                    if (currentGraph instanceof GraphTask) {
                        nodeTaskCreated.add((GraphTask) currentGraph);
                    }
                    nodeCreatedIds.add(currentTask.getTask().getId());
                }
            }
        }
    }

    private static boolean isAllLinkToEnd(RelationType relationType) {
        return relationType == RelationType.PARALLEL || relationType == RelationType.CHOICE;
    }

    private static List<TaskRun> findTaskRuns(Task task, Execution execution, TaskRun parent) {
        List<TaskRun> taskRuns = execution != null ? execution.findTaskRunsByTaskId(task.getId()) : new ArrayList<>();

        if (taskRuns.isEmpty()) {
            return Collections.singletonList(null);
        }

        return taskRuns
            .stream()
            .filter(taskRun -> parent == null || (taskRun.getParentTaskRunId().equals(parent.getId())))
            .toList();
    }
}
