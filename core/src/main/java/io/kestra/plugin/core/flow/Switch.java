package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.validations.SwitchTaskValidation;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks conditionally, i.e. decide which branch of tasks should be executed based on a given value.",
    description = "This task runs a set of tasks based on a given value.\n" +
        "The value is evaluated at runtime and compared to the list of cases.\n" +
        "If the value matches a case, the corresponding tasks are executed.\n" +
        "If the value does not match any case, the default tasks are executed."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: switch
                namespace: company.team

                inputs:
                  - id: string
                    type: STRING
                    required: true

                tasks:
                  - id: switch
                    type: io.kestra.plugin.core.flows.Switch
                    value: "{{ inputs.string }}"
                    cases:
                      FIRST:
                        - id: first
                          type: io.kestra.plugin.core.debug.Return
                          format: "{{ task.id }} > {{ taskrun.startDate }}"
                      SECOND:
                        - id: second
                          type: io.kestra.plugin.core.debug.Return
                          format: "{{ task.id }} > {{ taskrun.startDate }}"
                      THIRD:
                        - id: third
                          type: io.kestra.plugin.core.debug.Return
                          format: "{{ task.id }} > {{ taskrun.startDate }}"
                    defaults:
                      - id: default
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.Switch"
)
@Introspected
@SwitchTaskValidation
public class Switch extends Task implements FlowableTask<Switch.Output> {
    @NotBlank
    @NotNull
    @Schema(
        title = "The value to be evaluated."
    )
    @PluginProperty(dynamic = true)
    private String value;

    // @FIXME: @Valid break on io.micronaut.validation.validator.DefaultValidator#cascadeToOne with "Cannot validate java.util.ArrayList"
    // @Valid
    @Schema(
        title = "The map of keys and a list of tasks to be executed if the conditional `value` matches the key."
    )
    @PluginProperty
    private Map<String, List<Task>> cases;

    @Valid
    @PluginProperty
    private List<Task> defaults;

    @Valid
    @PluginProperty
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

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
                    Stream.concat(
                        this.errors != null ? this.errors.stream() : Stream.empty(),
                        this._finally != null ? this._finally.stream() : Stream.empty()
                    )
                )
            )
            .toList();
    }

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.CHOICE);

        GraphUtils.switchCase(
            subGraph,
            Stream
                .concat(
                    this.defaults != null ? ImmutableMap.of("defaults", this.defaults).entrySet().stream() : Stream.empty(),
                    this.cases != null ? this.cases.entrySet().stream() : Stream.empty()
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
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
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public Switch.Output outputs(RunContext runContext) throws IllegalVariableEvaluationException {
        return Output.builder()
            .value(rendererValue(runContext))
            .defaults(cases
                .entrySet()
                .stream()
                .noneMatch(throwPredicate(entry -> entry.getKey().equals(rendererValue(runContext))))
            )
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String value;
        private boolean defaults;
    }
}
