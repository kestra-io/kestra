package org.kestra.core.tasks.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.NextTaskRun;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.RelationType;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import java.util.Collections;
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
        "The value must be valid json string representing an arrays, like `[\"value1\", \"value2\"]` and must be a string\n" +
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
                "    type: org.kestra.core.tasks.debugs.Return",
                "    format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
            }
        ),
        @Example(
            title = "Handling each value in parralel but only 1 child task for each value at the same time.",
            code = {
                "value: '[\"value 1\", \"value 2\", \"value 3\"]'",
                "tasks:",
                "  - id: seq",
                "    type: org.kestra.core.tasks.flows.Sequential",
                "    tasks:",
                "    - id: t1",
                "      type: org.kestra.core.tasks.scripts.Bash",
                "      commands:",
                "        - 'echo \"{{task.id}} > {{ parents.0.taskrun.value }}",
                "        - 'sleep 1'",
                "    - id: t2",
                "      type: org.kestra.core.tasks.scripts.Bash",
                "      commands:",
                "        - 'echo \"{{task.id}} > {{ parents.0.taskrun.value }}",
                "        - 'sleep 1'"
            }
        )
    }
)
public class EachParallel extends Parallel implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    private String value;

    @Valid
    protected List<Task> errors;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        return TreeService.parallel(
            this.getTasks(),
            this.errors,
            Collections.singletonList(ParentTaskTree.builder()
                .id(this.getId())
                .value(this.value)
                .build()
            ),
            execution,
            RelationType.DYNAMIC,
            groups
        );
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
            parentTaskRun
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveParallelNexts(
            execution,
            FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), this.value),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
