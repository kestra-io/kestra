package io.kestra.plugin.core.flow;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.VariablesService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.validations.NoSystemLabelValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Call another flow as a subflow.",
    description = """
        Starts a separate execution of `namespace`/`flowId` (optionally a specific revision), passing inputs and labels, and optionally waits for completion. If the parent restarts, previously started subflows are restarted too.

        Use `wait`/`transmitFailed` to control propagation of the subflow result back to the parent."""
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
    }
)
public class Subflow extends Task implements ExecutableTask<Subflow.Output>, ChildFlowInterface {
    @NotEmpty
    @Schema(
        title = "The namespace of the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "The identifier of the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The revision of the subflow to be executed",
        description = "By default, the last, i.e., the most recent, revision of the subflow is executed."
    )
    @PluginProperty(dynamic = true)
    @Min(value = 1)
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the subflow to be executed",
        implementation = Object.class, oneOf = { List.class, Map.class }
    )
    @PluginProperty(dynamic = true)
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    private List<@NoSystemLabelValidation Label> labels;

    @Builder.Default
    @Schema(
        title = "Flag specifying whether to wait for the subflow execution to finish before continuing the current execution."
    )
    @PluginProperty
    private final Boolean wait = true;

    @Builder.Default
    @Schema(
        title = "Flag specifying whether to fail the current execution if the subflow execution fails or is killed.",
        description = "Note that this option works only if `wait` is set to `true`."
    )
    @PluginProperty
    private final Boolean transmitFailed = true;

    @Builder.Default
    @Schema(
        title = "Flag specifying whether the subflow should inherit labels from this execution that triggered it.",
        description = "By default, labels are not passed to the subflow execution. If you set this option to `true`, the child flow execution will inherit all labels from the parent execution."
    )
    private final Property<Boolean> inheritLabels = Property.ofValue(false);

    @Schema(
        title = "Don't trigger the subflow now but schedule it on a specific date."
    )
    private Property<ZonedDateTime> scheduleDate;

    @Schema(
        title = "Action to take when a failed execution is restarting",
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
        FlowMetaStoreInterface flowExecutorInterface,
        FlowInterface currentFlow,
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
            runContext.render(inheritLabels).as(Boolean.class).orElseThrow(),
            scheduleDate
        )
            .<List<SubflowExecution<?>>> map(subflowExecution -> List.of(subflowExecution))
            .orElse(Collections.emptyList());
    }

    @Override
    public Optional<SubflowExecutionResult> createSubflowExecutionResult(
        RunContext runContext,
        TaskRun taskRun,
        FlowInterface flow,
        Execution execution,
        Map<String, Object> outputs) {
        // we only create a worker task result when the execution is terminated
        if (!taskRun.getState().isTerminated()) {
            return Optional.empty();
        }

        final Output.OutputBuilder builder = Output.builder()
            .executionId(execution.getId())
            .state(execution.getState().getCurrent());

        VariablesService variablesService = ((DefaultRunContext) runContext).services().variablesService();
        if (this.wait) { // we only compute outputs if we wait for the subflow
            List<io.kestra.core.models.flows.Output> subflowOutputs = flow.getOutputs();

            if (subflowOutputs != null && !subflowOutputs.isEmpty()) {
                try {
                    var inputAndOutput = runContext.inputAndOutput();
                    Map<String, Object> rOutputs = inputAndOutput.renderOutputs(subflowOutputs);

                    if (flow.getOutputs() != null) {
                        rOutputs = inputAndOutput.typedOutputs(flow, execution, rOutputs);
                    }
                    builder.outputs(rOutputs);
                } catch (Exception e) {
                    Variables variables = variablesService.of(StorageContext.forTask(taskRun), builder.build());
                    return failSubflowDueToOutput(runContext, taskRun, execution, e, variables);
                }
            }
        }

        State.Type finalState = ExecutableUtils.guessState(execution, this.transmitFailed, this.isAllowFailure(), this.isAllowWarning());
        if (taskRun.getState().getCurrent() != finalState) {
            taskRun = taskRun.withState(finalState);
        }

        if (finalState.isFailed()) {
            String log = String
                .format("Subflow execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]] ends in FAILED state", execution.getId(), execution.getFlowId(), execution.getNamespace());
            runContext.logger().error(log);
        } else if (finalState == State.Type.WARNING) {
            String log = String
                .format("Subflow execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]] ends in WARNING state", execution.getId(), execution.getFlowId(), execution.getNamespace());
            runContext.logger().warn(log);
        }

        return Optional.of(ExecutableUtils.subflowExecutionResult(taskRun, builder.build().toMap(), execution));
    }

    private Optional<SubflowExecutionResult> failSubflowDueToOutput(RunContext runContext, TaskRun taskRun, Execution execution, Exception e, Map<String, Object> outputs) {
        runContext.logger().error("Failed to extract outputs with the error: '{}'", e.getLocalizedMessage(), e);
        var state = State.Type.fail(this);
        taskRun = taskRun
            .withState(state)
            .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()));

        return Optional.of(
            SubflowExecutionResult.builder()
                .executionId(execution.getId())
                .state(State.Type.FAILED)
                .parentTaskRun(taskRun)
                .outputs(outputs)
                .build()
        );
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
            title = "The subflow execution ID"
        )
        private final String executionId;

        @Schema(
            title = "The final state of the subflow execution",
            description = "This output is only available if `wait` is set to `true`."
        )
        private final State.Type state;

        @Schema(
            title = "The outputs returned by the subflow execution"
        )
        private final Map<String, Object> outputs;
    }
}
