package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
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
import org.kestra.core.models.tasks.TaskValidationInterface;
import org.kestra.core.models.validations.ManualConstraintViolation;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static org.kestra.core.utils.Rethrow.throwFunction;
import static org.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Process some tasks conditionnaly depending on a contextual value",
    description = "Allow some workflow based on context variables, allow you to branch your based on previous task."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: switch",
                "namespace: org.kestra.tests",
                "",
                "inputs:",
                "  - name: string",
                "    type: STRING",
                "    required: true",
                "",
                "tasks:",
                "  - id: switch",
                "    type: org.kestra.core.tasks.flows.Switch",
                "    value: \"{{inputs.string}}\"",
                "    cases:",
                "      FIRST:",
                "        - id: 1st",
                "          type: org.kestra.core.tasks.debugs.Return",
                "          format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "      SECOND:",
                "        - id: 2nd",
                "          type: org.kestra.core.tasks.debugs.Return",
                "          format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "      THIRD:",
                "        - id: 3th",
                "          type: org.kestra.core.tasks.debugs.Return",
                "          format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "    defaults:",
                "      - id: default",
                "        type: org.kestra.core.tasks.debugs.Return",
                "        format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class Switch extends Task implements FlowableTask<Switch.Output>, TaskValidationInterface<Switch> {
    @NotBlank
    @NotNull
    private String value;

    @Valid
    private Map<String, List<Task>> cases;

    @Valid
    private List<Task> defaults;

    @Valid
    protected List<Task> errors;

    private String rendererValue(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.value);
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.defaults != null ? this.defaults.stream() : Stream.empty(),
                Stream.concat(
                    this.cases != null ? this.cases.values().stream().flatMap(Collection::stream) : Stream.empty(),
                    this.errors != null ? this.errors.stream() : Stream.empty()
                )
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        return Stream
            .concat(
                this.defaults != null ? ImmutableMap.of("defaults", this.defaults).entrySet().stream() : Stream.empty(),
                this.cases != null ? this.cases.entrySet().stream() : Stream.empty()
            )
            .flatMap(throwFunction(e -> {
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
            }))
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return cases
            .entrySet()
            .stream()
            .filter(throwPredicate(entry -> entry.getKey().equals(rendererValue(runContext))))
            .map(Map.Entry::getValue)
            .map(tasks -> FlowableUtils.resolveTasks(tasks, parentTaskRun))
            .findFirst()
            .orElse(FlowableUtils.resolveTasks(this.defaults, parentTaskRun));
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
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

    @Override
    public Switch.Output outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return Output.builder()
            .value(rendererValue(runContext))
            .defaults(cases
                .entrySet()
                .stream()
                .noneMatch(throwPredicate(entry -> entry.getKey().equals(rendererValue(runContext))))
            )
            .build();
    }

    @Override
    public List<ConstraintViolation<Switch>> failedConstraints() {
        if ((this.cases == null || this.cases.size() == 0) && (this.defaults == null || this.defaults.size() == 0)) {
            return Collections.singletonList(ManualConstraintViolation.of(
                "No task defined, neither cases or default have any tasks",
                this,
                Switch.class,
                "switch.tasks",
                this.getId()
            ));
        }

        return Collections.emptyList();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        private String value;
        private boolean defaults;
    }
}
