package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks sequentially, one after the other, in the order they are defined.",
    description = "Used to visually group tasks."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: sequential",
                "namespace: company.team",
                "",
                "tasks:",
                "  - id: sequential",
                "    type: io.kestra.plugin.core.flow.Sequential",
                "    tasks:",
                "      - id: 1st",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{ task.id }} > {{ taskrun.startDate }}\"",
                "      - id: 2nd",
                "        type: io.kestra.plugin.core.debug.Return",
                "        format: \"{{ task.id }} > {{ taskrun.id }}\"",
                "  - id: last",
                "    type: io.kestra.plugin.core.debug.Return",
                "    format: \"{{ task.id }} > {{ taskrun.startDate }}\""
            }
        )
    },
    aliases = "io.kestra.core.tasks.flows.Sequential"
)
public class Sequential extends Task implements FlowableTask<VoidOutput> {
    @Valid
    protected List<Task> errors;

    @Valid
    @PluginProperty
    // FIXME -> issue with Pause @NotEmpty
    private List<Task> tasks;

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

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
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.getTasks() != null ? this.getTasks().stream() : Stream.empty(),
                this.getErrors() != null ? this.getErrors().stream() : Stream.empty()
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }
}
