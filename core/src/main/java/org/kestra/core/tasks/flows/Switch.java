package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.RelationType;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Switch extends Task implements FlowableTask<Switch.Output> {
    private String value;

    @Valid
    private Map<String, List<Task>> cases;

    @Valid
    private List<Task> defaults;

    private String rendererValue(RunContext runContext) {
        try {
            return runContext.render(this.value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) {
        return Stream
            .concat(
                this.defaults != null ? ImmutableMap.of("defaults", this.defaults).entrySet().stream() : Stream.empty(),
                this.cases != null ? this.cases.entrySet().stream() : Stream.empty()
            )
            .flatMap(e -> {
                List<ParentTaskTree> parents = Collections.singletonList((ParentTaskTree.builder()
                    .id(this.id)
                    .value(e.getKey())
                    .build()));

                return TreeService.sequential(
                    e.getValue(),
                    this.getErrors(),
                    parents,
                    execution,
                    RelationType.CHOICE,
                    groups
                ).stream();
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        return cases
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().equals(rendererValue(runContext)))
            .map(Map.Entry::getValue)
            .map(tasks -> FlowableUtils.resolveTasks(tasks, parentTaskRun))
            .findFirst()
            .orElse(FlowableUtils.resolveTasks(this.defaults, parentTaskRun));
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public Switch.Output outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return Output.builder()
            .value(rendererValue(runContext))
            .defaults(cases
                .entrySet()
                .stream().noneMatch(entry -> entry.getKey().equals(rendererValue(runContext)))
            )
            .build();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        private String value;
        private boolean defaults;
    }
}
