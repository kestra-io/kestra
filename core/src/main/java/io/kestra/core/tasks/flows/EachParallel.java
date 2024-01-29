package io.kestra.core.tasks.flows;

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
import lombok.*;
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
    title = "For each value in the list, execute one or more tasks in parallel.",
    description = "The list of `tasks` will be executed for each item in parallel. " +
        "The value must be a valid JSON string representing an array, e.g. a list of strings `[\"value1\", \"value2\"]` or a list of dictionaries `[{\"key\": \"value1\"}, {\"key\": \"value2\"}]`.\n" +
        "You can access the current iteration value using the variable `{{ taskrun.value }}`.\n\n" +
        "The task list will be executed in parallel for each item. For example, if you have a list with 3 elements and 2 tasks defined in the list of `tasks`, all " +
        "6 tasks will be computed in parallel without any order guarantee.\n\n" +
        "If you want to execute a group of sequential tasks for each value in parallel, you can wrap the list of `tasks` " +
        "with the [Sequential task](https://kestra.io/plugins/core/tasks/flows/io.kestra.core.tasks.flows.sequential).\n" +
        "If your list of values is large, you can limit the number of concurrent tasks using the `concurrent` property.\n\n" +
        "We highly recommend triggering a subflow for each value instead of specifying many tasks wrapped in a `Sequential` task. " +
        "This allows much better scalability and modularity. Check the [flow best practices documentation](https://kestra.io/docs/developer-guide/best-practice) " +
        "and the [following Blueprint](https://demo.kestra.io/ui/blueprints/community/128) for more details."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: each-parallel",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: each-parallel",
                "    type: io.kestra.core.tasks.flows.EachParallel",
                "    value: '[\"value 1\", \"value 2\", \"value 3\"]'",
                "    tasks:",
                "      - id: each-value",
                "        type: io.kestra.core.tasks.debugs.Return",
                "        format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
            }
        ),
        @Example(
            full = true,
            code = {
                "id: each-parallel",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: each-parallel",
                "    type: io.kestra.core.tasks.flows.EachParallel",
                "    value: ",
                "      - value 1",
                "      - value 2",
                "      - value 3",
                "    tasks:",
                "      - id: each-value",
                "        type: io.kestra.core.tasks.debugs.Return",
                "        format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
            }
        ),
        @Example(
            title = "Handling each value in parallel but only 1 child task for each value at the same time.",
            code = {
                "id: each-parallel",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: each-parallel",
                "    type: io.kestra.core.tasks.flows.EachParallel",
                "    value: '[\"value 1\", \"value 2\", \"value 3\"]'",
                "    tasks:",
                "      - id: seq",
                "        type: io.kestra.core.tasks.flows.Sequential",
                "        tasks:",
                "        - id: t1",
                "          type: io.kestra.plugin.scripts.shell.Commands",
                "          commands:",
                "            - 'echo \"{{task.id}} > {{ parents[0].taskrun.value }}",
                "            - 'sleep 1'",
                "        - id: t2",
                "          type: io.kestra.plugin.scripts.shell.Commands",
                "          commands:",
                "            - 'echo \"{{task.id}} > {{ parents[0].taskrun.value }}",
                "            - 'sleep 1'"
            }
        )
    }
)
public class EachParallel extends Parallel implements FlowableTask<VoidOutput> {
    @NotNull
    @Builder.Default
    @Schema(
        title = "Number of concurrent parallel tasks that can be running at any point in time.",
        description = "If the value is `0`, no limit exist and all the tasks will start at the same time."
    )
    @PluginProperty
    private final Integer concurrent = 0;

    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for this task.",
        description = "The value can be passed as a string, a list of strings, or a list of objects.",
        anyOf = {String.class, Object[].class}
    )
    private Object value;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        GraphUtils.parallel(
            subGraph,
            this.getTasks(),
            this.errors,
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
        return FlowableUtils.resolveParallelNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrent
        );
    }
}
