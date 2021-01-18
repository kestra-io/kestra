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
import org.kestra.core.models.hierarchies.GraphCluster;
import org.kestra.core.models.hierarchies.RelationType;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.GraphService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;

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
                "namespace: org.kestra.tests\n" +
                    "",
                "tasks:\n" +
                    "  - id: parallel\n" +
                    "    type: org.kestra.core.tasks.flows.Parallel\n" +
                    "    tasks:\n" +
                    "      - id: 1st\n" +
                    "        type: org.kestra.core.tasks.debugs.Return\n" +
                    "        format: \"{{task.id}} > {{taskrun.startDate}}\"\n" +
                    "      - id: 2nd\n" +
                    "        type: org.kestra.core.tasks.debugs.Return\n" +
                    "        format: \"{{task.id}} > {{taskrun.id}}\"\n" +
                    "  - id: last\n" +
                    "    type: org.kestra.core.tasks.debugs.Return\n" +
                    "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class Parallel extends Task implements FlowableTask<VoidOutput> {
    private Integer concurrent;

    @Valid
    private List<Task> tasks;

    @Valid
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
            parentTaskRun
        );
    }
}
