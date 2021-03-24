package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;

import java.util.*;
import java.util.stream.Collectors;

public class GraphService {
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

        while (iterator.hasNext()) {
            Task currentTask = iterator.next();
            for (TaskRun currentTaskRun : findTaskRuns(currentTask, execution, parent)) {
                AbstractGraphTask currentGraph;
                List<String> parentValues = null;
                Relation relation = new Relation(
                    relationType == RelationType.ERROR ? relationType : graph.getRelationType(),
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
