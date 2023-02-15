package io.kestra.core.tasks.flows;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Process task in parallel",
    description = "This task processes tasks in parallel. It makes it convinient to process many tasks at once."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: parallel",
                "namespace: io.kestra.tests\n" +
                    "",
                "tasks:\n" +
                    "  - id: parallel\n" +
                    "    type: io.kestra.core.tasks.flows.Parallel\n" +
                    "    tasks:\n" +
                    "      - id: 1st\n" +
                    "        type: io.kestra.core.tasks.debugs.Return\n" +
                    "        format: \"{{task.id}} > {{taskrun.startDate}}\"\n" +
                    "      - id: 2nd\n" +
                    "        type: io.kestra.core.tasks.debugs.Return\n" +
                    "        format: \"{{task.id}} > {{taskrun.id}}\"\n" +
                    "  - id: last\n" +
                    "    type: io.kestra.core.tasks.debugs.Return\n" +
                    "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class Parallel extends Task implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    @Builder.Default
    @Schema(
        title = "Number of concurrent parrallels tasks",
        description = "If the value is `0`, no limit exist and all the tasks will start at the same time"
    )
    @PluginProperty
    private final Integer concurrent = 0;

    @Valid
    @PluginProperty
    private List<Task> tasks;

    @Valid
    @PluginProperty
    private List<Task> errors;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.PARALLEL);

        GraphService.parallel(
            subGraph,
            this.tasks,
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
                this.tasks != null ? this.tasks.stream() : Stream.empty(),
                this.errors != null ? this.errors.stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.tasks, parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveParallelNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun,
            this.concurrent
        );
    }
}
