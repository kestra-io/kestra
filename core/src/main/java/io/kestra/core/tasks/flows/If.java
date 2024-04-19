package io.kestra.core.tasks.flows;

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
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SequentialNextsContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.TruthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
            code = {
                "id: if",
                "namespace: io.kestra.tests",
                "",
                "inputs:",
                "  - id: string",
                "    type: STRING",
                "    required: true",
                "",
                "tasks:",
                "  - id: if",
                "    type: io.kestra.core.tasks.flows.If",
                "    condition: \"{{ inputs.string == 'Condition' }}\"",
                "    then:",
                "      - id: when_true",
                "        type: io.kestra.core.tasks.log.Log",
                "        message: 'Condition was true'",
                "    else:",
                "      - id: when_false",
                "        type: io.kestra.core.tasks.log.Log",
                "        message: 'Condition was false'",
            }
        )
    }
)
public class If extends Task implements FlowableTask<VoidOutput> {
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
                    this.errors != null ? this.errors.stream() : Stream.empty())
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        String rendered = runContext.render(condition);
        if (TruthUtils.isTruthy(rendered)) {
            return FlowableUtils.resolveTasks(then, parentTaskRun);
        }
        return FlowableUtils.resolveTasks(_else, parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        SequentialNextsContext sequentialNextsContext = new SequentialNextsContext(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
        return FlowableUtils.resolveSequentialNexts(
           sequentialNextsContext
        );
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTask = this.childTasks(runContext, parentTaskRun);
        if (childTask == null) {
            // no next task to run, we guess the state from the parent task
            return Optional.of(execution.guessFinalState(null, parentTaskRun, this.isAllowFailure()));
        }
        SequentialNextsContext sequentialNextsContext = new SequentialNextsContext(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
        return FlowableUtils.resolveState(
           sequentialNextsContext,
            runContext,
            this.isAllowFailure()
        );
    }
}
