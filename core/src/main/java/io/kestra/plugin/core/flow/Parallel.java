package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.kestra.core.utils.GraphUtils;

import java.util.List;
import java.util.stream.Stream;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks in parallel.",
    description = "This task runs all child tasks in parallel."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: parallel
                namespace: company.team

                tasks:
                  - id: parallel
                    type: io.kestra.plugin.core.flow.Parallel
                    tasks:
                      - id: 1st
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} > {{ taskrun.startDate }}"

                      - id: 2nd
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} > {{ taskrun.id }}"

                  - id: last
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.Parallel"
)
public class Parallel extends Task implements FlowableTask<VoidOutput> {
    @NotNull
    @Builder.Default
    @Schema(
        title = "Number of concurrent parallel tasks that can be running at any point in time.",
        description = "If the value is `0`, no limit exist and all tasks will start at the same time."
    )
    @PluginProperty
    private final Integer concurrent = 0;

    @Valid
    @PluginProperty
    @NotEmpty
    @NotNull
    private List<@NotNull Task> tasks;

    @Valid
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
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.PARALLEL);

        GraphUtils.parallel(
            subGraph,
            this.tasks,
            this.errors,
            this._finally,
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
                Stream.concat(
                    this.errors != null ? this.errors.stream() : Stream.empty(),
                    this._finally != null ? this._finally.stream() : Stream.empty()
                )
            )
            .toList();
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
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun,
            this.concurrent
        );
    }
}
