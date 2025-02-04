package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.validations.NoSystemLabelValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.experimental.SuperBuilder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a subflow execution. Subflows offer a modular way to reuse workflow logic by calling other flows just like calling a function in a programming language.",
    description = "Restarting a parent flow will restart any subflows that has previously been executed."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a subflow with custom inputs.",
            full = true,
            code = """
                id: parent_flow
                namespace: company.team

                tasks:
                  - id: call_subflow
                    type: io.kestra.plugin.core.flow.Subflow
                    namespace: company.team
                    flowId: subflow
                    inputs:
                      user: Rick Astley
                      favorite_song: Never Gonna Give You Up
                    wait: true
                    transmitFailed: true
                """
        )
    },
    aliases = {"io.kestra.core.tasks.flows.Subflow", "io.kestra.core.tasks.flows.Flow"}
)
public class Subflow extends Task implements ExecutableTask<Subflow.Output>, ChildFlowInterface {

    static final String PLUGIN_FLOW_OUTPUTS_ENABLED = "outputs.enabled";

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
    @Min(value = 1)
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the subflow to be executed."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the subflow to be executed.",
        implementation = Object.class, oneOf = {List.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    private List<@NoSystemLabelValidation Label> labels;

    @Builder.Default
    @Schema(
        title = "Whether to wait for the subflow execution to finish before continuing the current execution."
    )
    @PluginProperty
    private final Boolean wait = true;

    @Builder.Default
    @Schema(
        title = "Whether to fail the current execution if the subflow execution fails or is killed.",
        description = "Note that this option works only if `wait` is set to `true`."
    )
    @PluginProperty
    private final Boolean transmitFailed = true;

    @Builder.Default
    @Schema(
        title = "Whether the subflow should inherit labels from this execution that triggered it.",
        description = "By default, labels are not passed to the subflow execution. If you set this option to `true`, the child flow execution will inherit all labels from the parent execution."
    )
    @PluginProperty
    private final Boolean inheritLabels = false;

    /**
     * @deprecated Output value should now be defined part of the Flow definition.
     */
    @Schema(
        title = "Outputs from the subflow executions.",
        description = "Allows to specify outputs as key-value pairs to extract any outputs from the subflow execution into output of this task execution." +
            "This property is deprecated since v0.15.0, please use the `outputs` property on the Subflow definition for defining the output values available and exposed to this task execution."
    )
    @PluginProperty(dynamic = true)
    @Deprecated(since = "0.15.0")
    private Map<String, Object> outputs;

    @Schema(
        title = "Don't trigger the subflow now but schedule it on a specific date."
    )
    private Property<ZonedDateTime> scheduleDate;

    @Schema(
        title = "What to do when a failed execution is restarting.",
        description = """
            - RETRY_FAILED (default): will restart the subflow execution if it's failed.
            - NEW_EXECUTION: will create a new subflow execution.""
            """
    )
    @NotNull
    @Builder.Default
    private RestartBehavior restartBehavior = RestartBehavior.RETRY_FAILED;

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

        return ExecutableUtils.subflowExecution(
            runContext,
            flowExecutorInterface,
            currentExecution,
            currentFlow,
            this,
            currentTaskRun,
            inputs,
            labels,
            inheritLabels,
            scheduleDate
        )
            .<List<SubflowExecution<?>>>map(subflowExecution -> List.of(subflowExecution))
            .orElse(Collections.emptyList());
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

        boolean isOutputsAllowed = runContext
            .<Boolean>pluginConfiguration(PLUGIN_FLOW_OUTPUTS_ENABLED)
            .orElse(true);

        final Output.OutputBuilder builder = Output.builder()
            .executionId(execution.getId())
            .state(execution.getState().getCurrent());

        final Map<String, Object> subflowOutputs = Optional
            .ofNullable(flow.getOutputs())
            .map(outputs -> outputs
                .stream()
                .collect(Collectors.toMap(
                    io.kestra.core.models.flows.Output::getId,
                    io.kestra.core.models.flows.Output::getValue)
                )
            )
            .orElseGet(() -> isOutputsAllowed ? this.getOutputs() : null);

        if (subflowOutputs != null) {
            try {
                Map<String, Object> outputs = runContext.render(subflowOutputs);
                FlowInputOutput flowInputOutput = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowInputOutput.class); // this is hacking
                if (flow.getOutputs() != null && flowInputOutput != null) {
                    outputs = flowInputOutput.typedOutputs(flow, execution, outputs);
                }
                builder.outputs(outputs);
            } catch (Exception e) {
                runContext.logger().warn("Failed to extract outputs with the error: '{}'", e.getLocalizedMessage(), e);
                var state = this.isAllowFailure() ? this.isAllowWarning() ? State.Type.SUCCESS : State.Type.WARNING : State.Type.FAILED;
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

        State.Type finalState = ExecutableUtils.guessState(execution, this.transmitFailed, this.isAllowFailure(), this.isAllowWarning());
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
