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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "For each value in the list, execute one or more tasks sequentially.",
    description = "The list of `tasks` will be executed for each item sequentially. " +
        "The value must be a valid JSON string representing an array, e.g. a list of strings `[\"value1\", \"value2\"]` or a list of dictionaries `[{\"key\": \"value1\"}, {\"key\": \"value2\"}]`. \n\n" +
        "You can access the current iteration value using the variable `{{ taskrun.value }}`. " +
        "The task list will be executed sequentially for each item.\n\n" +
        "We highly recommend triggering a subflow for each value. " +
        "This allows much better scalability and modularity. Check the [flow best practices documentation](https://kestra.io/docs/developer-guide/best-practice) " +
        "and the [following Blueprint](https://demo.kestra.io/ui/blueprints/community/128) for more details."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "value: '[\"value 1\", \"value 2\", \"value 3\"]'",
                "tasks:",
                "  - id: each-value",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
            }
        ),
        @Example(
            code = {
                "value: ",
                "- value 1",
                "- value 2",
                "- value 3",
                "tasks:",
                "  - id: each-value",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
            }
        ),
    }
)
public class EachSequential extends Sequential implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for this task",
        description = "The value car be passed as a String, a list of String, or a list of objects",
        anyOf = {String.class, Object[].class}
    )
    private Object value;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        GraphUtils.sequential(
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

        if (childTasks.size() == 0) {
            return Optional.of(State.Type.SUCCESS);
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun,
            runContext
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
