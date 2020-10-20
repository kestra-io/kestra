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
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.hierarchies.ParentTaskTree;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Process tasks ones after others sequentially",
    description = "Mostly use in order to group tasks."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: sequential",
                "namespace: org.kestra.tests",
                "",
                "tasks:",
                "  - id: sequential",
                "    type: org.kestra.core.tasks.flows.Sequential",
                "    tasks:",
                "      - id: 1st",
                "        type: org.kestra.core.tasks.debugs.Return",
                "        format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "      - id: 2nd",
                "        type: org.kestra.core.tasks.debugs.Return",
                "        format: \"{{task.id}} > {{taskrun.id}}\"",
                "  - id: last",
                "    type: org.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class Sequential extends Task implements FlowableTask<VoidOutput> {
    @Valid
    protected List<Task> errors;

    @Valid
    @NotEmpty
    private List<Task> tasks;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        return TreeService.sequential(
            this.tasks,
            this.errors,
            Collections.singletonList(ParentTaskTree.builder()
                .id(this.getId())
                .build()
            ),
            execution,
            groups
        );
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
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
