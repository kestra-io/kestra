package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TruthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Process tasks conditionally depending on a contextual value.",
    description = "Allow some workflow based on context variables, for example, branch a flow based on a previous task."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: if
                namespace: company.team

                inputs:
                  - id: string
                    type: STRING
                    required: true

                tasks:
                  - id: if
                    type: io.kestra.plugin.core.flow.If
                    condition: "{{ inputs.string == 'Condition' }}"
                    then:
                      - id: when_true
                        type: io.kestra.plugin.core.log.Log
                        message: "Condition was true"
                    else:
                      - id: when_false
                        type: io.kestra.plugin.core.log.Log
                        message: "Condition was false"
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.If"
)
public class If extends Task implements FlowableTask<If.Output> {
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The `If` condition which can be any expression that evaluates to a boolean value.",
        description = "Boolean coercion allows 0, -0, null and '' to evaluate to false, all other values will evaluate to true."
    )
    private String condition;

    @Valid
    @PluginProperty
    @Schema(
        title = "List of tasks to execute if the condition is true."
    )
    @NotEmpty
    private List<Task> then;

    @Valid
    @PluginProperty
    @Schema(
        title = "List of tasks to execute if the condition is false."
    )
    @JsonProperty("else")
    private List<Task> _else;

    @Valid
    @PluginProperty
    @Schema(
        title = "List of tasks to execute in case of errors of a child task."
    )
    private List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Override
    public List<Task> getErrors() {
        return errors;
    }

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.CHOICE);

        GraphUtils.ifElse(
            subGraph,
            this.then,
            this._else,
            this._finally,
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
                this.then != null ? this.then.stream() : Stream.empty(),
                Stream.concat(
                    this._else != null ? this._else.stream() : Stream.empty(),
                    Stream.concat(
                        this.errors != null ? this.errors.stream() : Stream.empty(),
                        this._finally != null ? this._finally.stream() : Stream.empty()
                    )
                )
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        // We need to evaluate the condition once, so if the condition is impacted during the processing or a branch, the same branch is always taken.
        // This can exist for ex if the condition is based on a KV and the KV is changed in the branch.
        // For this, we evaluate the condition in the outputs() method and get it from the outputs.
        // But unfortunately, the output may not have yet been computed in some cases, like if the task is inside a flowable, in this case we compute the result anyway.
        Boolean evaluationResult;
        if (parentTaskRun.getOutputs() == null || parentTaskRun.getOutputs().get("evaluationResult") == null) {
            evaluationResult = isTrue(runContext);
        } else {
            evaluationResult = (Boolean) parentTaskRun.getOutputs().get("evaluationResult");
        }

        if (Boolean.TRUE.equals(evaluationResult)) {
            return FlowableUtils.resolveTasks(then, parentTaskRun);
        }
        return FlowableUtils.resolveTasks(_else, parentTaskRun);
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
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = ListUtils.emptyOnNull(this.childTasks(runContext, parentTaskRun)).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();
        if (ListUtils.isEmpty(childTasks)) {
            // no next task to run, we guess the state from the parent task
            return Optional.of(execution.guessFinalState(null, parentTaskRun, this.isAllowFailure(), this.isAllowWarning()));
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    @Override
    public If.Output outputs(RunContext runContext) throws Exception {
        Boolean evaluationResult = isTrue(runContext);
        return If.Output.builder().evaluationResult(evaluationResult).build();
    }

    private Boolean isTrue(RunContext runContext) throws IllegalVariableEvaluationException {
        String rendered = runContext.render(condition);
        return TruthUtils.isTruthy(rendered);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Condition evaluation result.")
        public Boolean evaluationResult;
    }
}
