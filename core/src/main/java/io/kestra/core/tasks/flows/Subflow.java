package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a subflow execution. Subflows offer a modular way to reuse workflow logic by calling other flows just like calling a function in a programming language."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a subflow with custom inputs.",
            code = {
                "namespace: dev",
                "flowId: subflow",
                "inputs:",
                "  user: \"Rick Astley\"",
                "  favorite_song: \"Never Gonna Give You Up\"",
                "wait: true",
                "transmitFailed: true"
            }
        )
    }
)
public class Subflow extends Task implements ExecutableTask<Subflow.Output> {
    @NotEmpty
    @Schema(
        title = "The namespace of the subflow to be executed."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "The identifier of the subflow to be executed."
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The revision of the subflow to be executed.",
        description = "By default, the last, i.e. the most recent, revision of the subflow is executed."
    )
    @PluginProperty(dynamic = true)
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the subflow to be executed."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the subflow to be executed."
    )
    @PluginProperty(dynamic = true)
    private Map<String, String> labels;

    @Builder.Default
    @Schema(
        title = "Whether to wait for the subflow execution to finish before continuing the current execution."
    )
    @PluginProperty
    private final Boolean wait = false;

    @Builder.Default
    @Schema(
        title = "Whether to fail the current execution if the subflow execution fails or is killed.",
        description = "Note that this option works only if `wait` is set to `true`."
    )
    @PluginProperty
    private final Boolean transmitFailed = false;

    @Builder.Default
    @Schema(
        title = "Whether the subflow should inherit labels from this execution that triggered it.",
        description = "By default, labels are not passed to the subflow execution. If you set this option to `true`, the child flow execution will inherit all labels from the parent execution."
    )
    @PluginProperty
    private final Boolean inheritLabels = false;

    @Schema(
        title = "Outputs from the subflow executions.",
        description = "Allows to specify outputs as key-value pairs to extract any outputs from the subflow execution into output of this task execution."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> outputs;

    @Override
    public List<SubflowExecution<?>> createSubflowExecutions(RunContext runContext,
                                                             FlowExecutorInterface flowExecutorInterface,
                                                             io.kestra.core.models.flows.Flow currentFlow,
                                                             Execution currentExecution,
                                                             TaskRun currentTaskRun) throws InternalException {
        Map<String, Object> inputs = new HashMap<>();
        if (this.inputs != null) {
            inputs.putAll(runContext.render(this.inputs));
        }

        List<Label> labels = new ArrayList<>();
        if (this.inheritLabels && currentExecution.getLabels() != null && !currentExecution.getLabels().isEmpty()) {
            labels.addAll(currentExecution.getLabels());
        }

        if (this.labels != null) {
            for (Map.Entry<String, String> entry: this.labels.entrySet()) {
                labels.add(new Label(entry.getKey(), runContext.render(entry.getValue())));
            }
        }

        return List.of(ExecutableUtils.subflowExecution(
            runContext,
            flowExecutorInterface,
            currentExecution,
            currentFlow,
            this,
            currentTaskRun,
            inputs,
            labels
        ));
    }

    @Override
    public Optional<SubflowExecutionResult> createSubflowExecutionResult(
        RunContext runContext,
        TaskRun taskRun,
        io.kestra.core.models.flows.Flow flow,
        Execution execution
    ) {
        // we only create a worker task result when the execution is terminated
        if (!taskRun.getState().isTerminated()) {
            return Optional.empty();
        }

        Output.OutputBuilder builder = Output.builder()
            .executionId(execution.getId())
            .state(execution.getState().getCurrent());
        if (this.getOutputs() != null) {
            try {
                builder.outputs(runContext.render(this.getOutputs()));
            } catch (Exception e) {
                runContext.logger().warn("Failed to extract outputs with the error: '" + e.getMessage() + "'", e);
                var state = this.isAllowFailure() ? State.Type.WARNING : State.Type.FAILED;
                taskRun = taskRun
                    .withState(state)
                    .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()))
                    .withOutputs(builder.build().toMap());

                return Optional.of(SubflowExecutionResult.builder()
                    .executionId(execution.getId())
                    .state(State.Type.FAILED)
                    .parentTaskRun(taskRun)
                    .build());
            }
        }

        taskRun = taskRun.withOutputs(builder.build().toMap());

        State.Type finalState = ExecutableUtils.guessState(execution, this.transmitFailed, this.isAllowFailure());
        if (taskRun.getState().getCurrent() != finalState) {
            taskRun = taskRun.withState(finalState);
        }

        return Optional.of(ExecutableUtils.subflowExecutionResult(taskRun, execution));
    }

    @Override
    public boolean waitForExecution() {
        return this.wait;
    }

    @Override
    public SubflowId subflowId() {
        return new SubflowId(this.namespace, this.flowId, Optional.ofNullable(this.revision));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The ID of the subflow execution."
        )
        private final String executionId;

        @Schema(
            title = "The final state of the subflow execution.",
            description = "This output is only available if `wait` is set to `true`."
        )
        private final State.Type state;

        @Schema(
            title = "The extracted outputs from the subflow execution."
        )
        private final Map<String, Object> outputs;
    }
}
