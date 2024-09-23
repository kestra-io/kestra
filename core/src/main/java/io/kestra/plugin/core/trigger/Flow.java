package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Kestra is able to trigger flow after another one. This allows chaining flows without need to update the base flows.\n" +
        "With that, you can break the responsibility between different flows, and thus to different teams.",
    description = "::alert{type=\"warning\"}\n" +
        "If you don't provide any conditions, the flow will be triggered for **EVERY execution** of **EVERY flow** on your instance.\n" +
        "::"
)
@Plugin(
    examples = @Example(
        title = "This flow will be triggered after each successful execution of flow `io.kestra.tests.trigger-flow` " +
            "and forward the `uri` of `myTask` taskId outputs.",
        full = true,
        code = "id: trigger-flow-listener\n" +
            "namespace: io.kestra.tests\n" +
            "\n" +
            "inputs:\n" +
            "  - id: from-parent\n" +
            "    type: STRING\n" +
            "\n" +
            "tasks:\n" +
            "  - id: only-no-input\n" +
            "    type: io.kestra.plugin.core.debug.Return\n" +
            "    format: \"v1: {{ trigger.executionId }}\"\n" +
            "\n" +
            "triggers:\n" +
            "  - id: listen-flow\n" +
            "    type: io.kestra.plugin.core.trigger.Flow\n" +
            "    inputs:\n" +
            "      from-parent: '{{ outputs.myTask.uri }}'\n" +
            "    conditions:\n" +
            "      - type: io.kestra.plugin.cores.condition.ExecutionFlowCondition\n" +
            "        namespace: io.kestra.tests\n" +
            "        flowId: trigger-flow\n" +
            "      - type: io.kestra.plugin.core.condition.ExecutionStatusCondition\n" +
            "        in:\n" +
            "          - SUCCESS"

    ),
    aliases = "io.kestra.core.models.triggers.types.Flow"
)
public class Flow extends AbstractTrigger implements TriggerOutput<Flow.Output> {

    private static final String TRIGGER_VAR = "trigger";
    private static final String OUTPUTS_VAR = "outputs";

    @Nullable
    @Schema(
        title = "Fill input of this flow based on output of current flow, allowing to pass data or file to the triggered flow.",
        description = "::alert{type=\"warning\"}\n" +
            "If you provide invalid input, the flow will not be created! Since there is no task started, you can't log any reason that's visible on the Execution UI.\n" +
            "So you will need to go to the Logs tabs on the UI to understand the error.\n" +
            "::"
    )
    @PluginProperty
    private Map<String, Object> inputs;

    public Optional<Execution> evaluate(RunContext runContext, io.kestra.core.models.flows.Flow flow, Execution current) {
        Logger logger = runContext.logger();

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .labels(generateLabels(runContext, flow))
            .state(new State())
            .trigger(ExecutionTrigger.of(
                this,
                Output.builder()
                    .executionId(current.getId())
                    .namespace(current.getNamespace())
                    .flowId(current.getFlowId())
                    .flowRevision(current.getFlowRevision())
                    .state(current.getState().getCurrent())
                    .build()
            ));

        try {
            if (this.inputs != null) {
                Map<String, Object> outputs = current.getOutputs();
                if (outputs != null && !outputs.isEmpty()) {
                    builder.inputs(runContext.render(this.inputs, Map.of(TRIGGER_VAR, Map.of(OUTPUTS_VAR, outputs))));
                } else {
                    builder.inputs(runContext.render(this.inputs));
                }
            } else {
                builder.inputs(new HashMap<>());
            }
            return Optional.of(builder.build());
        } catch (Exception e) {
            logger.warn(
                "Failed to trigger flow {}.{} for trigger {}, invalid inputs",
                flow.getNamespace(),
                flow.getId(),
                this.getId(),
                e
            );
            return Optional.empty();
        }
    }

    private List<Label> generateLabels(RunContext runContext, io.kestra.core.models.flows.Flow flow) {
        final List<Label> labels = new ArrayList<>();

        if (flow.getLabels() != null) {
            labels.addAll(flow.getLabels()); // no need for rendering
        }

        if (this.getLabels() != null) {
            for (Label label : this.getLabels()) {
                final var value = renderLabelValue(runContext, label);
                if (value != null) {
                    labels.add(new Label(label.key(), value));
                }
            }
        }

        return labels;
    }

    private String renderLabelValue(RunContext runContext, Label label) {
        try {
            return runContext.render(label.value());
        } catch (IllegalVariableEvaluationException e) {
            runContext.logger().warn("Failed to render label '{}', it will be omitted", label.key(), e);
            return null;
        }
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The execution ID that triggered the current flow.")
        @NotNull
        private String executionId;

        @Schema(title = "The execution state.")
        @NotNull
        private State.Type state;

        @Schema(title = "The namespace of the flow that triggered the current flow.")
        @NotNull
        private String namespace;

        @Schema(title = "The flow ID whose execution triggered the current flow.")
        @NotNull
        private String flowId;

        @Schema(title = "The flow revision that triggered the current flow.")
        @NotNull
        private Integer flowRevision;
    }
}
