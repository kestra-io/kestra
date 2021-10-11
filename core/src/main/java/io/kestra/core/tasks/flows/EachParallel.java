package io.kestra.core.tasks.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.GraphService;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a tasks for a list of value in parallel.",
    description = "For each `value`, `tasks` will be executed\n" +
        "The value must be valid json string representing an arrays, like `[\"value1\", \"value2\"]` or `[{\"key\":\"value1\"}, {\"key\":\"value2\"}]` \n" +
        "The current value is available on vars `{{ taskrun.value }}`.\n" +
        "The task list will be executed in parallel, for example if you have a 3 value with each one 2 tasks, all the " +
        "6 tasks will be computed in parallel with out any garantee on the order.\n" +
        "If you want to have each value in parallel, but no concurrent task for each value, you need to wrap the tasks " +
        "with a `Sequential` tasks"
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
            title = "Handling each value in parralel but only 1 child task for each value at the same time.",
            code = {
                "value: '[\"value 1\", \"value 2\", \"value 3\"]'",
                "tasks:",
                "  - id: seq",
                "    type: io.kestra.core.tasks.flows.Sequential",
                "    tasks:",
                "    - id: t1",
                "      type: io.kestra.core.tasks.scripts.Bash",
                "      commands:",
                "        - 'echo \"{{task.id}} > {{ parents[0].taskrun.value }}",
                "        - 'sleep 1'",
                "    - id: t2",
                "      type: io.kestra.core.tasks.scripts.Bash",
                "      commands:",
                "        - 'echo \"{{task.id}} > {{ parents[0].taskrun.value }}",
                "        - 'sleep 1'"
            }
        )
    }
)
public class EachParallel extends Parallel implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    @Builder.Default
    @Schema(
        title = "Number of concurrent parrallels tasks",
        description = "If the value is `0`, no limit exist and all the tasks will start at the same time"
    )
    private final Integer concurrent = 0;

    @NotNull
    @NotBlank
    private String value;

    @Valid
    protected List<Task> errors;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        GraphService.parallel(
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
        return FlowableUtils.resolveParallelNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrent
        );
    }
}
