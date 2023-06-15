package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.GraphService;
import io.kestra.core.validations.DagTaskValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@DagTaskValidation
@Schema(
    title = "Create a directed acyclic graph (DAG) flow without bothering with the graph structure.",
    description = "List your tasks and their dependencies, and Kestra will figure out the rest.\n" +
        "Task can only depends on task from the DAG tasks.\n" +
        "For technical reasons, low-code interaction with this Task is disabled for now."
)
public class Dag extends Task implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    @Builder.Default
    @Schema(
        title = "Number of concurrent parallel tasks",
        description = "If the value is `0`, no limit exist and all the tasks will start at the same time"
    )
    @PluginProperty
    private final Integer concurrent = 0;

    @NotEmpty
    private List<DagTask> tasks;

    @Valid
    @PluginProperty
    protected List<Task> errors;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        this.controlTask();

        GraphService.dag(
            subGraph,
            this.getTasks(),
            this.errors,
            taskRun,
            execution
        );

        return subGraph;
    }

    private void controlTask() throws IllegalVariableEvaluationException {
        List<String> dagCheckNotExistTasks = this.dagCheckNotExistTask(this.tasks);
        if (!dagCheckNotExistTasks.isEmpty()) {
            throw new IllegalVariableEvaluationException("Some task doesn't exists on task '" + this.id + "': " +  String.join(", ", dagCheckNotExistTasks));
        }

        ArrayList<String> cyclicDependenciesTasks = this.dagCheckCyclicDependencies(this.tasks);
        if (!cyclicDependenciesTasks.isEmpty()) {
            throw new IllegalVariableEvaluationException("Infinite loop detected on task '" + this.id + "': " + String.join(", ", cyclicDependenciesTasks));
        }
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.tasks != null ? this.tasks.stream().map(DagTask::getTask) : Stream.empty(),
                this.errors != null ? this.errors.stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.tasks.stream().map(DagTask::getTask).toList(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        this.controlTask();

        return FlowableUtils.resolveDagNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrent,
            this.tasks
        );
    }

    public List<String> dagCheckNotExistTask(List<DagTask> taskDepends) {
        List<String> dependenciesIds = taskDepends
            .stream()
            .map(DagTask::getDependsOn)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .toList();

        List<String> tasksIds = taskDepends
            .stream()
            .map(taskDepend -> taskDepend.getTask().getId())
            .toList();

        return dependenciesIds.stream()
            .filter(dependencyId -> !tasksIds.contains(dependencyId))
            .collect(Collectors.toList());
    }

    public ArrayList<String> dagCheckCyclicDependencies(List<DagTask> taskDepends) {
        ArrayList<String> cyclicDependency = new ArrayList<>();
        taskDepends.forEach(taskDepend -> {
            if (taskDepend.getDependsOn() != null) {
                List<String> nestedDependencies = this.nestedDependencies(taskDepend, taskDepends, new ArrayList<>());
                if (nestedDependencies.contains(taskDepend.getTask().getId())) {
                    cyclicDependency.add(taskDepend.getTask().getId());
                }
            }
        });

        return cyclicDependency;
    }

    private ArrayList<String> nestedDependencies(DagTask taskDepend, List<DagTask> tasks, List<String> visited) {
        final ArrayList<String> localVisited = new ArrayList<>(visited);
        if (taskDepend.getDependsOn() != null) {
            taskDepend.getDependsOn()
                .stream()
                .filter(depend -> !localVisited.contains(depend))
                .forEach(depend -> {
                    localVisited.add(depend);
                    Optional<DagTask> task = tasks
                        .stream()
                        .filter(t -> t.getTask().getId().equals(depend))
                        .findFirst();

                    if (task.isPresent()) {
                        localVisited.addAll(this.nestedDependencies(task.get(), tasks, localVisited));
                    }
                });
        }
        return localVisited;
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DagTask {
        @NotNull
        @PluginProperty
        private Task task;

        @PluginProperty
        private List<String> dependsOn;
    }
}
