package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "For each value in the list, execute one or more tasks sequentially.",
    description = "This task is deprecated, please use the `io.kestra.plugin.core.flow.ForEach` task instead.\n\n" +
        "The list of `tasks` will be executed for each item sequentially. " +
        "The value must be a valid JSON string representing an array, e.g. a list of strings `[\"value1\", \"value2\"]` or a list of dictionaries `[{\"key\": \"value1\"}, {\"key\": \"value2\"}]`. \n\n" +
        "You can access the current iteration value using the variable `{{ taskrun.value }}`. " +
        "The task list will be executed sequentially for each item.\n\n" +
        "We highly recommend triggering a subflow for each value. " +
        "This allows much better scalability and modularity. Check the [flow best practices documentation](https://kestra.io/docs/best-practices/flows) " +
        "for more details."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "The taskrun.value from the `each_sequential` task is available only to immediate child tasks such as the `before_if` and the `if` tasks. To access the taskrun value in child tasks of the `if` task (such as in the `after_if` task), you need to use the syntax `{{ parent.taskrun.value }}` as this allows you to access the taskrun value of the parent task `each_sequential`.",
            code = """
                id: loop_example
                namespace: company.team

                tasks:
                  - id: each_sequential
                    type: io.kestra.plugin.core.flow.EachSequential
                    value: ["value 1", "value 2", "value 3"]
                    tasks:
                      - id: before_if
                        type: io.kestra.plugin.core.debug.Return
                        format: 'Before if {{ taskrun.value }}'
                      - id: if
                        type: io.kestra.plugin.core.flow.If
                        condition: '{{ taskrun.value == "value 2" }}'
                        then:
                          - id: after_if
                            type: io.kestra.plugin.core.debug.Return
                            format: "After if {{ parent.taskrun.value }}"
                """
        ),
        @Example(
            full = true,
            title = "This task shows that the value can be a bullet-style list. The task iterates over the list of values and executes the `each_value` child task for each value.",
            code = """
                id: each_sequential_flow
                namespace: company.team

                tasks:
                  - id: each_sequential
                    type: io.kestra.plugin.core.flow.EachSequential
                    value:
                      - value 1
                      - value 2
                      - value 3
                    tasks:
                      - id: each_value
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} with value '{{ taskrun.value }}'"
                """
        ),
    },
    aliases = "io.kestra.core.tasks.flows.EachSequential"
)
@Deprecated(since = "0.19", forRemoval = true)
public class EachSequential extends Sequential implements FlowableTask<VoidOutput> {
    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for this task.",
        description = "The value car be passed as a string, a list of strings, or a list of objects.",
        oneOf = {String.class, Object[].class}
    )
    private Object value;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        GraphUtils.sequential(
            subGraph,
            this.getTasks(),
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = ListUtils.emptyOnNull(this.childTasks(runContext, parentTaskRun)).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();
        if (childTasks.isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }
}
