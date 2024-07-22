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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a group of tasks for each value in the list.",
    description = """
        You can control how many task groups are executed concurrently by setting the `concurrencyLimit` property. \

        - If you set the `concurrencyLimit` property to `0`, Kestra will execute all task groups concurrently for all values. \

        - If you set the `concurrencyLimit` property to `1`, Kestra will execute each task group one after the other starting with the task group for the first value in the list. \


        Regardless of the `concurrencyLimit` property, the `tasks` will run one after the other — to run those in parallel, wrap them in a [Parallel](https://kestra.io/plugins/core/tasks/flow/io.kestra.plugin.core.flow.parallel) task as shown in the last example below (_see the flow `parallel_tasks_example`_). \


        The `values` should be defined as a JSON string or an array, e.g. a list of string values `["value1", "value2"]` or a list of key-value pairs `[{"key": "value1"}, {"key": "value2"}]`.\s


        You can access the current iteration value using the variable `{{ taskrun.value }}` \
        or `{{ parent.taskrun.value }}` if you are in a nested child task. \


        If you need to execute more than 2-5 tasks for each value, we recommend triggering a subflow for each value for better performance and modularity. \
        Check the [flow best practices documentation](https://kestra.io/docs/best-practices/flows) for more details."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = """
                The `{{ taskrun.value }}` from the `for_each` task is available only to direct child tasks \
                such as the `before_if` and the `if` tasks. To access the taskrun value of the parent task \
                in a nested child task such as the `after_if` task, use `{{ parent.taskrun.value }}`.""",
            code = """
                id: for_loop_example
                namespace: company.team

                tasks:
                  - id: for_each
                    type: io.kestra.plugin.core.flow.ForEach
                    values: ["value 1", "value 2", "value 3"]
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
                            format: 'After if {{ parent.taskrun.value }}'"""
        ),
        @Example(
            full = true,
            title = """
                This flow uses YAML-style array for `values`. The task `for_each` iterates over a list of values \
                and executes the `return` child task for each value. The `concurrencyLimit` property is set to 2, \
                so the `return` task will run concurrently for the first two values in the list at first. \
                The `return` task will run for the next two values only after the task runs for the first two values \
                have completed.""",
            code = {
                "id: for_each_value",
                "namespace: company.team",
                "",
                "tasks:",
                "  - id: for_each",
                "    type: io.kestra.plugin.core.flow.ForEach",
                "    values: ",
                "      - value 1",
                "      - value 2",
                "      - value 3",
                "      - value 4",
                "    concurrencyLimit: 2",
                "    tasks:",
                "      - id: return",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{ task.id }} with value '{{ taskrun.value }}'\"",
            }
        ),
        @Example(
            full = true,
            title = """
                This example shows how to run tasks in parallel for each value in the list. \
                All child tasks of the `parallel` task will run in parallel. \
                However, due to the `concurrencyLimit` property set to 2, \
                only two `parallel` task groups will run at any given time.""",
            code = """
                id: parallel_tasks_example
                namespace: company.team

                tasks:
                  - id: for_each
                    type: io.kestra.plugin.core.flow.ForEach
                    values: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
                    concurrencyLimit: 2
                    tasks:
                      - id: parallel
                        type: io.kestra.plugin.core.flow.Parallel
                        tasks:
                        - id: log
                          type: io.kestra.plugin.core.log.Log
                          message: Processing {{ parent.taskrun.value }}
                        - id: shell
                          type: io.kestra.plugin.scripts.shell.Commands
                          commands:
                            - sleep {{ parent.taskrun.value }}"""
        ),
    }
)
public class ForEach extends Sequential implements FlowableTask<VoidOutput> {
    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for which Kestra will execute a group of tasks.",
        description = "The values can be passed as a string, a list of strings, or a list of objects.",
        oneOf = {String.class, Object[].class}
    )
    private Object values;

    @NotNull
    @Builder.Default
    @Schema(
        title = "The number of concurrent task groups for each value in the `values` array.",
        description = """
        If you set the `concurrencyLimit` property to 0, Kestra will execute all task groups concurrently for all values (zero limits!). \


        If you set the `concurrencyLimit` property to 1, Kestra will execute each task group one after the other starting with the first value in the list (limit concurrency to one task group that can be actively running at any time)."""
    )
    @PluginProperty
    private final Integer concurrencyLimit = 1;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        GraphUtils.parallel(
            subGraph,
            this.getTasks(),
            this.getErrors(),
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.values);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        if (childTasks.isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure()
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.concurrencyLimit == 1) {
            return FlowableUtils.resolveSequentialNexts(
                execution,
                this.childTasks(runContext, parentTaskRun),
                FlowableUtils.resolveTasks(this.errors, parentTaskRun),
                parentTaskRun
            );
        }

        return FlowableUtils.resolveConcurrentNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.values),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrencyLimit
        );
    }
}
