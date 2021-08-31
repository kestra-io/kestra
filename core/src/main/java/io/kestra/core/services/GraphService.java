package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphService {
    public static GraphCluster of(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        GraphCluster graph = new GraphCluster();

        GraphService.sequential(
            graph,
            flow.getTasks(),
            flow.getErrors(),
            null,
            execution
        );

        return graph;
    }

    public static List<AbstractGraphTask> nodes(GraphCluster graphCluster) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> t instanceof GraphCluster ? nodes((GraphCluster) t).stream() : Stream.of(t))
            .distinct()
            .collect(Collectors.toList());
    }

    private static List<Triple<AbstractGraphTask, AbstractGraphTask, Relation>> rawEdges(GraphCluster graphCluster) {
        return Stream.concat(
                graphCluster.getGraph().edges()
                    .stream()
                    .map(r -> Triple.of(r.getSource(), r.getTarget(), r.getValue())),
                graphCluster.getGraph().nodes()
                    .stream()
                    .flatMap(t -> t instanceof GraphCluster ? rawEdges((GraphCluster) t).stream() : Stream.of())
            )
            .collect(Collectors.toList());
    }

    public static List<FlowGraph.Edge> edges(GraphCluster graphCluster) {
        return rawEdges(graphCluster)
            .stream()
            .map(r -> new FlowGraph.Edge(r.getLeft().getUid(), r.getMiddle().getUid(), r.getRight()))
            .collect(Collectors.toList());
    }

    public static List<Pair<GraphCluster, List<String>>> clusters(GraphCluster graphCluster, List<String> parents) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> {

                if (t instanceof GraphCluster) {
                    ArrayList<String> currentParents = new ArrayList<>(parents);
                    currentParents.add(t.getUid());

                    return Stream.concat(
                        Stream.of(Pair.of((GraphCluster) t, parents)),
                        clusters((GraphCluster) t, currentParents).stream()
                    );
                }

                return Stream.of();
            })
            .collect(Collectors.toList());
    }

    public static void sequential(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        iterate(graph, tasks, errors, parent, execution, RelationType.SEQUENTIAL);
    }

    public static void parallel(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        iterate(graph, tasks, errors, parent, execution, RelationType.PARALLEL);
    }

    public static void switchCase(
        GraphCluster graph,
        Map<String, List<Task>> tasks,
        List<Task> errors,
        TaskRun parent,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        for (Map.Entry<String, List<Task>> entry: tasks.entrySet()) {
            fillGraph(graph, entry.getValue(), RelationType.SEQUENTIAL, parent, execution, entry.getKey());
        }

        // error cases
        if (errors != null && errors.size() > 0) {
            fillGraph(graph, errors, RelationType.ERROR, parent, execution, null);
        }
    }

    private static void iterate(
        GraphCluster graph,
        List<Task> tasks,
        List<Task> errors,
        TaskRun parent,
        Execution execution,
        RelationType relationType
    ) throws IllegalVariableEvaluationException {
        // standard cases
        fillGraph(graph, tasks, relationType, parent, execution, null);

        // error cases
        if (errors != null && errors.size() > 0) {
            fillGraph(graph, errors, RelationType.ERROR, parent, execution, null);
        }
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
        AbstractGraphTask previous = graph.getRoot();

        boolean isFirst = true;
        while (iterator.hasNext()) {
            Task currentTask = iterator.next();
            for (TaskRun currentTaskRun : findTaskRuns(currentTask, execution, parent)) {
                AbstractGraphTask currentGraph;
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
                    parentValues = execution.findChildsValues(currentTaskRun, true);
                }

                // detect kind
                if (currentTask instanceof FlowableTask) {
                    FlowableTask<?> flowableTask = ((FlowableTask<?>) currentTask);
                    currentGraph = flowableTask.tasksTree(execution, currentTaskRun, parentValues);
                } else {
                    currentGraph = new GraphTask(currentTask, currentTaskRun, parentValues, relationType);
                }

                // add the node
                graph.getGraph().addNode(currentGraph);

                // link to previous one
                if (previous != null) {
                    graph.getGraph().addEdge(
                        previous instanceof GraphCluster ? ((GraphCluster) previous).getEnd() : previous,
                        currentGraph instanceof GraphCluster ? ((GraphCluster) currentGraph).getRoot() : currentGraph,
                        relation
                    );
                }

                // change previous for current one to link
                if (relationType != RelationType.PARALLEL) {
                    previous = currentGraph;
                }

                // link to end task
                if (GraphService.isAllLinkToEnd(relationType)) {
                    graph.getGraph().addEdge(
                        currentGraph instanceof GraphCluster ? ((GraphCluster) currentGraph).getEnd() : currentGraph,
                        graph.getEnd(),
                        new Relation()
                    );
                }

                isFirst = false;

                if (!iterator.hasNext() && !isAllLinkToEnd(relationType)) {
                    graph.getGraph().addEdge(
                        currentGraph instanceof GraphCluster ? ((GraphCluster) currentGraph).getEnd() : currentGraph,
                        graph.getEnd(),
                        new Relation()
                    );
                }
            }
        }
    }

    private static boolean isAllLinkToEnd(RelationType relationType) {
        return relationType == RelationType.PARALLEL || relationType == RelationType.CHOICE;
    }

    private static List<TaskRun> findTaskRuns(Task task, Execution execution, TaskRun parent) {
        List<TaskRun> taskRuns = execution != null ? execution.findTaskRunsByTaskId(task.getId()) : new ArrayList<>();

        if (taskRuns.size() == 0) {
            return Collections.singletonList(null);
        }

        return taskRuns
            .stream()
            .filter(taskRun -> parent == null  || (taskRun.getParentTaskRunId().equals(parent.getId())))
            .collect(Collectors.toList());
    }
}
