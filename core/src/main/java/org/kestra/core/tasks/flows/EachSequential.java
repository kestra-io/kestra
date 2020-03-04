package org.kestra.core.tasks.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.RelationType;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Execute a tasks for a list of value",
    body = {
        "For each `value`, `tasks` will be executed",
        "The value must be valid json string representing an arrays, like `[\"value1\", \"value2\"] and must be a string",
        "The current value is available on vars `{{ taskrun.value }}`."
    }
)
@Example(
    code = {
        "value: '[\"value 1\", \"value 2\", \"value 3\"]'",
        "tasks:",
        "  - id: each-value",
        "    type: org.kestra.core.tasks.debugs.Return",
        "    format: \"{{ task.id }} with current value '{{ taskrun.value }}'\"",
    }
)
public class EachSequential extends Sequential implements FlowableTask<VoidOutput> {
    private String value;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) {
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
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        return this.resolveTasks(runContext, parentTaskRun);
    }

    private List<ResolvedTask> resolveTasks(RunContext runContext, TaskRun parentTaskRun) {
        ObjectMapper mapper = new ObjectMapper();

        String[] values;
        try {
            String renderValue = runContext.render(this.value);
            values = mapper.readValue(renderValue, String[].class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Arrays
            .stream(values)
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
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
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
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.resolveTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
