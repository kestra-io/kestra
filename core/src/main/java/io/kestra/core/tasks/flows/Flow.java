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
    title = "Trigger another flow"
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger another flow, passing some files and arguments as inputs",
            code = {
                "namespace: io.kestra.tests",
                "flowId: my-sub-flows",
                "inputs:",
                "  file: \"{{ outputs.myTask.outputFiles.resolver }}\"",
                "  store: 12",
                "wait: false"
            }
        )
    }
)
public class Flow extends Task implements RunnableTask<Flow.Output> {
    @NotNull
    @Schema(
        title = "The namespace of the flow to trigger"
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "The identifier of the flow to trigger"
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The revision of the flow to trigger",
        description = "By default, we trigger the last version."
    )
    @PluginProperty(dynamic = true)
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the triggered flow"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the triggered flow execution"
    )
    @PluginProperty(dynamic = true)
    private Map<String, String> labels;

    @Builder.Default
    @Schema(
        title = "Wait the end of the execution.",
        description = "By default, we don't wait till the end of the flow, if you set to true, we wait the end of the trigger flow before continue this one."
    )
    @PluginProperty
    private final Boolean wait = false;

    @Builder.Default
    @Schema(
        title = "Failed the current execution if the waited execution is failed or killed.",
        description = "`wait` need to be true to make it work"
    )
    @PluginProperty
    private final Boolean transmitFailed = false;

    @Builder.Default
    @Schema(
        title = "Inherit labels from the calling execution",
        description = "By default, we don't inherit any labels of the calling execution"
    )
    @PluginProperty
    private final Boolean inheritLabels = false;

    @Schema(
        title = "Extract outputs from triggered executions.",
        description = "Allow to specify key value (with value rendered), in order to extract any outputs from " +
            "triggered execution."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> outputs;

    @Override
    public Flow.Output run(RunContext runContext) throws Exception {
        throw new IllegalStateException("This task must not be run by a worker and must be run on executor side!");
    }

    public String flowUid() {
        return io.kestra.core.models.flows.Flow.uid(this.getNamespace(), this.getFlowId(), Optional.ofNullable(this.revision));
    }

    public String flowUidWithoutRevision() {
        return io.kestra.core.models.flows.Flow.uidWithoutRevision(this.getNamespace(), this.getFlowId());
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
            namespace,
            flowId,
            revision,
            flowVars.get("namespace"),
            flowVars.get("id")
        )
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "'.'" + flowId + "' with revision + '" + revision + "'"));

        if (flow.isDisabled()) {
            throw new IllegalStateException("Cannot execute disabled flow");
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
                runContext.logger().warn("Failed to extract outputs with error: '" + e.getMessage() + "'", e);
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
            title = "The id of the execution trigger."
        )
        private final String executionId;

        @Schema(
            title = "The state of the execution trigger.",
            description = "Only available if the execution is waited with `wait` options"
        )
        private final State.Type state;

        @Schema(
            title = "The extracted outputs from triggered executions."
        )
        private final Map<String, Object> outputs;
    }
}
