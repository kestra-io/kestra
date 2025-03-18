package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.validations.DagTaskValidation;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;



@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@DagTaskValidation
@Schema(
    title = "Create a directed acyclic graph (DAG) of tasks without explicitly specifying the order in which the tasks need to run.",
    description = "List your tasks and their dependencies, and Kestra will figure out the execution sequence.\n" +
        "Each task can only depend on other tasks from the DAG task.\n" +
        "For technical reasons, low-code interaction via UI forms is disabled for now when using this task."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a series of tasks for which the execution order is defined by their upstream dependencies.",
            full = true,
            code = """
                id: dag_flow
                namespace: company.team
                tasks:
                  - id: dag
                    type: io.kestra.plugin.core.flow.Dag
                    tasks:
                      - task:
                          id: task1
                          type: io.kestra.plugin.core.log.Log
                          message: task 1
                      - task:
                          id: task2
                          type: io.kestra.plugin.core.log.Log
                          message: task 2
                        dependsOn:
                          - task1
                      - task:
                          id: task3
                          type: io.kestra.plugin.core.log.Log
                          message: task 3
                        dependsOn:
                          - task1
                      - task:
                          id: task4
                          type: io.kestra.plugin.core.log.Log
                          message: task 4
                        dependsOn:
                          - task2
                      - task:
                          id: task5
                          type: io.kestra.plugin.core.log.Log
                          message: task 5
                        dependsOn:
                          - task4
                          - task3
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.Dag"
)
public class Dag extends Task implements FlowableTask<VoidOutput> {
    @NotNull
    @Builder.Default
    @Schema(
        title = "Number of concurrent parallel tasks that can be running at any point in time.",
        description = "If the value is `0`, no concurrency limit exists for the tasks in a DAG and all tasks that can run in parallel will start at the same time."
    )
    @PluginProperty
    private final Integer concurrent = 0;

    @Valid
    @NotEmpty
    private List<DagTask> tasks;

    @Valid
    @PluginProperty
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        this.controlTask();

        GraphUtils.dag(
            subGraph,
            this.getTasks(),
            this.errors,
            this._finally,
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
                Stream.concat(
                    this.errors != null ? this.errors.stream() : Stream.empty(),
                    this._finally != null ? this._finally.stream() : Stream.empty()
                )
            )
            .toList();
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
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
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
            .toList();
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
    @Introspected
    public static class DagTask {
        @NotNull
        @Schema(
            title = "The task within the DAG."
        )
        @PluginProperty
        private Task task;

        @PluginProperty
        @Schema(
            title = "The list of task IDs that should have been successfully executed before starting this task."
        )
        private List<String> dependsOn;
    }
}
