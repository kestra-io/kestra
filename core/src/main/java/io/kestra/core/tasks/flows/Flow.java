package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
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
            title = "Run a subflow with custom inputs",
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
public class Flow extends Task implements RunnableTask<Flow.Output> {
    @NotNull
    @Schema(
        title = "The namespace of the flow that should be executed as a subflow"
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "The identifier of the flow that should be executed as a subflow"
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The revision of the flow that should be executed as a subflow",
        description = "By default, the last i.e. the most recent revision of the flow is triggered."
    )
    @PluginProperty(dynamic = true)
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the subflow"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the subflow execution"
    )
    @PluginProperty(dynamic = true)
    private Map<String, String> labels;

    @Builder.Default
    @Schema(
        title = "Whether to wait for the subflow execution to finish before continuing the current execution",
        description = "By default, the subflow will be executed in a fire-and-forget manner without waiting for the subflow execution to finish. If you set this option to `true`, the current execution will wait for the subflow execution to finish before continuing with the next task."
    )
    @PluginProperty
    private final Boolean wait = false;

    @Builder.Default
    @Schema(
        title = "Whether to fail the current execution if the subflow execution fails or is killed",
        description = "Note that this option only has an effect if `wait` is set to `true`."
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
        description = "Allows to specify key-value pairs (with the value rendered) in order to extract any outputs from the " +
            "subflow execution."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> outputs;

    @Override
    public Flow.Output run(RunContext runContext) throws Exception {
        throw new IllegalStateException("This task should not be executed by a worker and must run on executor side.");
    }

    public String flowUid() {
        // as the Flow task can only be used in the same tenant we can hardcode null here
        return io.kestra.core.models.flows.Flow.uid(null, this.getNamespace(), this.getFlowId(), Optional.ofNullable(this.revision));
    }

    public String flowUidWithoutRevision() {
        // as the Flow task can only be used in the same tenant we can hardcode null here
        return io.kestra.core.models.flows.Flow.uidWithoutRevision(null, this.getNamespace(), this.getFlowId());
    }

    @SuppressWarnings("unchecked")
    public Execution createExecution(RunContext runContext, FlowExecutorInterface flowExecutorInterface, Execution currentExecution) throws Exception {
        RunnerUtils runnerUtils = runContext.getApplicationContext().getBean(RunnerUtils.class);

        Map<String, Object> inputs = new HashMap<>();
        if (this.inputs != null) {
            inputs.putAll(runContext.render(this.inputs));
        }

        List<Label> labels = new ArrayList<>();
        if (this.inheritLabels) {
            labels.addAll(currentExecution.getLabels());
        }
        if (this.labels != null) {
            for (Map.Entry<String, String> entry: this.labels.entrySet()) {
                labels.add(new Label(entry.getKey(), runContext.render(entry.getValue())));
            }
        }

        Map<String, String> flowVars = (Map<String, String>) runContext.getVariables().get("flow");

        String namespace = runContext.render(this.namespace);
        String flowId = runContext.render(this.flowId);
        Optional<Integer> revision = this.revision != null ? Optional.of(this.revision) : Optional.empty();

        io.kestra.core.models.flows.Flow flow = flowExecutorInterface.findByIdFromFlowTask(
            flowVars.get("tenantId"),
            namespace,
            flowId,
            revision,
            flowVars.get("tenantId"),
            flowVars.get("namespace"),
            flowVars.get("id")
        )
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "'.'" + flowId + "' with revision + '" + revision + "'"));

        if (flow.isDisabled()) {
            throw new IllegalStateException("Cannot execute a flow which is disabled");
        }

        if (flow instanceof FlowWithException fwe) {
            throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
        }

        return runnerUtils
            .newExecution(
                flow,
                (f, e) -> runnerUtils.typedInputs(f, e, inputs),
                labels)
            .withTrigger(ExecutionTrigger.builder()
                .id(this.getId())
                .type(this.getType())
                .variables(ImmutableMap.of(
                    "executionId", ((Map<String, Object>) runContext.getVariables().get("execution")).get("id"),
                    "namespace", flowVars.get("namespace"),
                    "flowId", flowVars.get("id"),
                    "flowRevision", flowVars.get("revision")
                ))
                .build()
            );
    }

    public WorkerTaskResult createWorkerTaskResult(
        @Nullable RunContextFactory runContextFactory,
        WorkerTaskExecution workerTaskExecution,
        @Nullable io.kestra.core.models.flows.Flow flow,
        Execution execution
    ) {
        TaskRun taskRun = workerTaskExecution.getTaskRun();

        Output.OutputBuilder builder = Output.builder()
            .executionId(execution.getId());

        if (workerTaskExecution.getTask().getOutputs() != null && runContextFactory != null) {
            RunContext runContext = runContextFactory.of(
                flow,
                workerTaskExecution.getTask(),
                execution,
                workerTaskExecution.getTaskRun()
            );

            try {
                builder.outputs(runContext.render(workerTaskExecution.getTask().getOutputs()));
            } catch (Exception e) {
                runContext.logger().warn("Failed to extract outputs with the error: '" + e.getMessage() + "'", e);
                taskRun = taskRun
                    .withState(State.Type.FAILED)
                    .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build()))
                    .withOutputs(builder.build().toMap());

                return WorkerTaskResult.builder()
                    .taskRun(taskRun)
                    .build();
            }
        }

        builder.state(execution.getState().getCurrent());

        taskRun = taskRun.withOutputs(builder.build().toMap());

        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            taskRun = taskRun.withState(execution.getState().getCurrent());
        } else {
            taskRun = taskRun.withState(State.Type.SUCCESS);
        }

        return WorkerTaskResult.builder()
            .taskRun(taskRun.withAttempts(
                Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(taskRun.getState().getCurrent())).build())
            ))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The id of the subflow execution."
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
