package org.kestra.core.models.triggers.types;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionTrigger;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.TriggerOutput;
import org.kestra.core.runners.RunContext;
import org.kestra.core.utils.IdUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Kestra is able to trigger flow after another one. This allows chaining flow without need to update the base flows.\n" +
        "With that, you can break responsibility between different flow to different teams.",
    description = "::: warning\n" +
        "If you don't provide any conditions, the flow will be triggered for **EVERY execution** of **EVERY flow** on your instance.\n" +
        ":::"
)
@Plugin(
    examples = @Example(
        title = "This flow will be triggered after each successfully execution of flow `org.kestra.tests.trigger-flow` " +
            "and forward the `uri` of `my-task` taskId outputs.",
        code = "id: trigger-flow-listener\n" +
            "namespace: org.kestra.tests\n" +
            "revision: 1\n" +
            "\n" +
            "inputs:\n" +
            "  - name: from-parent\n" +
            "    type: STRING\n" +
            "\n" +
            "tasks:\n" +
            "  - id: only-no-input\n" +
            "    type: org.kestra.core.tasks.debugs.Return\n" +
            "    format: \"v1: {{trigger.executionId}}\"\n" +
            "\n" +
            "triggers:\n" +
            "  - id: listen-flow\n" +
            "    type: org.kestra.core.models.triggers.types.Flow\n" +
            "    inputs:\n" +
            "      from-parent: '{{ outputs.my-task.uri }}'\n" +
            "    conditions:\n" +
            "      - type: org.kestra.core.models.conditions.types.FlowCondition\n" +
            "        namespace: org.kestra.tests\n" +
            "        flowId: trigger-flow\n" +
            "      - type: org.kestra.core.models.conditions.types.ExecutionStatusCondition\n" +
            "        in:\n" +
            "          - SUCCESS"

    )
)
public class Flow extends AbstractTrigger implements TriggerOutput<Flow.Output> {
    @Nullable
    @Schema(
        title = "Fill input of this flow based on output of current flow, allowing to pass data or file on the triggered flow",
        description = "::: warning\n" +
            "If you provide invalid input, the flow will not be created! Since there is no task started, you can't log any reason visible on the execution ui.\n" +
            "So you will need to go to Logs tabs on the ui to understand the error\n" +
            ":::"
    )
    private Map<String, Object> inputs;

    public Optional<Execution> evaluate(RunContext runContext, org.kestra.core.models.flows.Flow flow, Execution current) {
        Logger logger = runContext.logger();

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .trigger(ExecutionTrigger.of(
                this,
                Output.builder()
                    .executionId(current.getId())
                    .namespace(current.getNamespace())
                    .flowId(current.getFlowId())
                    .flowRevision(current.getFlowRevision())
                    .build()
            ));

        try {
            builder.inputs(runContext.render(this.inputs == null ? new HashMap<>() : this.inputs));
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

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements org.kestra.core.models.tasks.Output {
        @Schema(title = "The execution id that trigger the current flow")
        @NotNull
        private String executionId;

        @Schema(title = "The namespace of the flow that trigger the current flow")
        @NotNull
        private String namespace;

        @Schema(title = "The execution id that trigger the current flow")
        @NotNull
        private String flowId;

        @Schema(title = "The flow revision that trigger the current flow")
        @NotNull
        private Integer flowRevision;
    }
}
