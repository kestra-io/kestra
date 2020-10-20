package org.kestra.core.tasks.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a tasks for a list of value",
    description = "For each `value`, `tasks` will be executed\n" +
        "The value must be valid json string representing an arrays, like `[\"value1\", \"value2\"]` and must be a string\n" +
        "The current value is available on vars `{{ taskrun.value }}`."
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
        )
    }
)
public class EachSequential extends Sequential implements FlowableTask<VoidOutput> {
    @NotNull
    @NotBlank
    private String value;

    @Valid
    protected List<Task> errors;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        return TreeService.sequential(
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
        return this.resolveTasks(runContext, parentTaskRun);
    }

    private List<ResolvedTask> resolveTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        ObjectMapper mapper = new ObjectMapper();

        String[] values;

        String renderValue = runContext.render(this.value);
        try {
            values = mapper.readValue(renderValue, String[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }

        return Arrays
            .stream(values)
            .distinct()
            .flatMap(value -> this.getTasks()
                .stream()
                .map(task -> ResolvedTask.builder()
                    .task(task)
                    .value(value)
                    .parentId(parentTaskRun.getId())
                    .build()
                )
            )
            .collect(Collectors.toList());
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
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.resolveTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
