package io.kestra.plugin.core.condition;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.triggers.TimeSLA;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a flow if the list of preconditions are met in a time window.",
    description = """
        This task is deprecated, use io.kestra.plugin.core.condition.ExecutionsCondition or io.kestra.plugin.core.condition.AdvancedExecutionsCondition instead.
        Will trigger an executions when all the flows defined by the preconditions are successfully executed in a specific period of time.
        The period is defined by the `timeSLA` property and is by default a duration window of 24 hours."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "A flow that is waiting for 2 flows to run successfully in a day",
            code = {
                "triggers:",
                "  - id: multiple-listen-flow",
                "    type: io.kestra.plugin.core.trigger.Flow",
                "    conditions:",
                "      - type: io.kestra.plugin.core.condition.ExecutionStatusCondition",
                "        in:",
                "        - SUCCESS",
                "      - id: multiple",
                "        type: io.kestra.plugin.core.condition.MultipleCondition",
                "        sla:",
                "          window: PT12H",
                "        conditions:",
                "          flow-a:",
                "            type: io.kestra.plugin.core.condition.ExecutionFlowCondition",
                "            namespace: io.kestra.demo",
                "            flowId: multiplecondition-flow-a",
                "          flow-b:",
                "            type: io.kestra.plugin.core.condition.ExecutionFlowCondition",
                "            namespace: io.kestra.demo",
                "            flowId: multiplecondition-flow-b"
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.MultipleCondition"
)
@Slf4j
@Deprecated
public class MultipleCondition extends AbstractMultipleCondition {
    @Schema(
        title = "The duration of the window",
        description = "Deprecated, use `timeSLA.window` instead.")
    @PluginProperty
    @Deprecated
    private Duration window;

    public void setWindow(Duration window) {
        this.window = window;
        this.timeSLA = this.getTimeSLA() == null ? TimeSLA.builder().window(window).build() : this.getTimeSLA().withWindow(window);
    }

    @Schema(
        title = "The window advance duration",
        description = "Deprecated, use `timeSLA.windowAdvance` instead.")
    @PluginProperty
    @Deprecated
    private Duration windowAdvance;

    public void setWindowAdvance(Duration windowAdvance) {
        this.windowAdvance = windowAdvance;
        this.timeSLA = this.getTimeSLA() == null ? TimeSLA.builder().windowAdvance(windowAdvance).build() : this.getTimeSLA().withWindowAdvance(windowAdvance);
    }

    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of preconditions to wait for",
        description = "The key must be unique for a trigger because it will be used to store the previous evaluation result."
    )
    @PluginProperty(
        additionalProperties = Condition.class
    )
    private Map<String, Condition> conditions;
}
