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
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@DagTaskValidation
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

    @NotNull
    @PluginProperty(dynamic = true)
    private List<TaskDepend> tasks;

    @Valid
    @PluginProperty
    protected List<Task> errors;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        if(this.dagCheckNotExistTask(this.tasks) != null || this.dagCheckCyclicDependencies(this.tasks) != null) {
            throw new IllegalVariableEvaluationException("Infinite loop detected in Dag tasks");
        }

        GraphService.dag(
            subGraph,
            this.getTasks(),
            this.errors,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.tasks != null ? this.tasks.stream().map(TaskDepend::getTask) : Stream.empty(),
                this.errors != null ? this.errors.stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.tasks.stream().map(TaskDepend::getTask).toList(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {

        if(this.dagCheckNotExistTask(this.tasks) != null || this.dagCheckCyclicDependencies(this.tasks) != null) {
            throw new IllegalVariableEvaluationException("Infinite loop detected in Dag tasks");
        }

        return FlowableUtils.resolveDagNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrent,
            this.tasks
        );
    }


    public List<String> dagCheckNotExistTask(List<TaskDepend> taskDepends) {
        List<String> dependenciesIds = taskDepends
            .stream()
            .map(TaskDepend::getDependsOn)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .toList();

        List<String> tasksIds = taskDepends.stream()
            .map(taskDepend -> taskDepend.getTask().getId())
            .toList();

        List<String> invalidDependencyIds = dependenciesIds.stream()
            .filter(dependencyId -> !tasksIds.contains(dependencyId))
            .collect(Collectors.toList());

        if (!invalidDependencyIds.isEmpty()) {

            return invalidDependencyIds;
        }

        return null;
    }

    public String dagCheckCyclicDependencies(List<TaskDepend> taskDepends) {
        AtomicReference<String> cyclicDependency = new AtomicReference<>();
        taskDepends.forEach(taskDepend -> {
            if (taskDepend.getDependsOn() != null) {
                List<String> nestedDependencies = this.nestedDependencies(taskDepend, taskDepends, new ArrayList<>());
                if (nestedDependencies.contains(taskDepend.getTask().getId())) {
                    cyclicDependency.set(taskDepend.getTask().getId());
                }
            }
        });

        return cyclicDependency.get();
    }

    private ArrayList<String> nestedDependencies(TaskDepend taskDepend, List<TaskDepend> tasks, List<String> visited) {
        final ArrayList<String> localVisited = new ArrayList<>();
        if(taskDepend.getDependsOn() != null) {
            taskDepend.getDependsOn().stream()
                .forEach(depend -> {
                    if(localVisited.contains(depend)) {
                        return;
                    }
                    localVisited.add(depend);
                    Optional<TaskDepend> task = tasks.stream()
                        .filter(t -> t.getTask().getId().equals(depend))
                        .findFirst();
                    if(task.isPresent()){
                        localVisited.addAll(nestedDependencies(task.get(), tasks, localVisited));
                    };
                });
        }
        return localVisited;
    }

}
