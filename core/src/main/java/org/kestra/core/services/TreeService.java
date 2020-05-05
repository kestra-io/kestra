package org.kestra.core.services;

import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.RelationType;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TreeService {
    public static List<TaskTree> sequential(
        List<Task> tasks,
        List<Task> errors,
        List<ParentTaskTree> parents,
        Execution execution
    ) throws IllegalVariableEvaluationException {
        return sequential(
            tasks,
            errors,
            parents,
            execution,
            null,
            new ArrayList<>()
        );
    }

    public static List<TaskTree> sequential(
        List<Task> tasks,
        List<Task> errors,
        List<ParentTaskTree> parents,
        Execution execution,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        return sequential(
            tasks,
            errors,
            parents,
            execution,
            null,
            groups
        );
    }

    public static List<TaskTree> sequential(
        List<Task> tasks,
        List<Task> errors,
        List<ParentTaskTree> parents,
        Execution execution,
        RelationType relationType,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        List<TaskTree> result = new ArrayList<>();

        // error cases
        if (errors != null && errors.size() > 0) {
            result.addAll(sequential(errors, parents, RelationType.ERROR, execution, groups));
        }

        // standard cases
        result.addAll(sequential(
            tasks, 
            parents,
            relationType == null ? RelationType.SEQUENTIAL : relationType,
            execution,
            groups
        ));

        return result;
    }

    private static List<TaskTree> sequential(
        List<Task> tasks,
        List<ParentTaskTree> parents,
        RelationType relationType,
        Execution execution,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        List<TaskTree> result = new ArrayList<>();

        for (Task task : tasks) {
            if (task instanceof FlowableTask) {
                FlowableTask<?> flowableTask = ((FlowableTask<?>) task);
                List<String> childs = new ArrayList<>(groups);
                childs.add(task.getId());

                result.addAll(toTaskTree(parents, task, relationType, execution, groups));

                result.addAll(flowableTask.tasksTree(task.getId(), execution, (childs)));
            } else {
                result.addAll(toTaskTree(parents, task, relationType, execution, groups));
            }

            parents = Collections.singletonList(ParentTaskTree.builder()
                .id(task.getId())
                .build());
            relationType = RelationType.SEQUENTIAL;
        }

        return result;
    }

    public static List<TaskTree> parallel(
        List<Task> tasks,
        List<Task> errors,
        List<ParentTaskTree> parents,
        Execution execution,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        return parallel(
            tasks,
            errors,
            parents,
            execution,
            null,
            groups
        );
    }
    
    public static List<TaskTree> parallel(
        List<Task> tasks,
        List<Task> errors,
        List<ParentTaskTree> parents,
        Execution execution,
        RelationType relationType,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        List<TaskTree> result = new ArrayList<>();

        // error cases
        if (errors != null && errors.size() > 0) {
            result.addAll(sequential(errors, parents, RelationType.ERROR, execution, groups));
        }

        // standard cases
        result.addAll(parallel(
            tasks, 
            parents,
            relationType == null ? RelationType.PARALLEL : relationType,
            execution,
            groups
        ));

        return result;
    }

    private static List<TaskTree> parallel(
        List<Task> tasks,
        List<ParentTaskTree> parents,
        RelationType relationType,
        Execution execution,
        List<String> groups
    ) throws IllegalVariableEvaluationException {
        List<TaskTree> result = new ArrayList<>();

        for (Task task : tasks) {
            if (task instanceof FlowableTask) {
                FlowableTask<?> flowableTask = ((FlowableTask<?>) task);
                List<String> childs = new ArrayList<>(groups);
                childs.add(task.getId());

                result.addAll(toTaskTree(parents, task, relationType, execution, groups));

                result.addAll(flowableTask.tasksTree(task.getId(), execution, (childs)));
            } else {
                result.addAll(toTaskTree(parents, task, relationType, execution, groups));
            }
        }

        return result;
    }
    
    public static List<TaskTree> toTaskTree(
        List<ParentTaskTree> parents,
        Task task,
        RelationType relationType,
        Execution execution,
        List<String> groups
    ) {
        List<TaskRun> taskRuns = execution != null ? execution.findTaskRunsByTaskId(task.getId()) : new ArrayList<>();

        if (taskRuns.size() == 0) {
            return Collections.singletonList(TaskTree.builder()
                .relation(relationType)
                .groups(groups)
                .task(task)
                .parent(parents == null ? new ArrayList<>() : parents)
                .build()
            );
        }

        return taskRuns
            .stream()
            .map(taskRun -> TaskTree.builder()
                .relation(relationType)
                .groups(groups)
                .task(task)
                .taskRun(taskRun)
                .parent(dynamicParentTaskTrees(parents, taskRun))
                .build())
            .collect(Collectors.toList());
    }

    private static List<ParentTaskTree> dynamicParentTaskTrees(List<ParentTaskTree> parents, TaskRun taskRun) {
        if (parents == null) {
            return new ArrayList<>();
        }

        return parents
            .stream()
            .map(parentTaskTree -> ParentTaskTree.builder()
                .id(parentTaskTree.getId())
                .value(taskRun.getValue())
                .build()
            )
            .collect(Collectors.toList());
    }
}
