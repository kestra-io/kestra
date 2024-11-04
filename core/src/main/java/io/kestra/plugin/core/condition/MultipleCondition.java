package io.kestra.plugin.core.condition;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a list of flows.",
    description = "Trigger when all the flows are successfully executed for the first time during the `window` duration."
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
                "        window: P1D",
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
public class MultipleCondition extends AbstractMultipleCondition {
    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of conditions to wait for",
        description = "The key must be unique for a trigger since it will be use to store previous result."
    )
    @PluginProperty(
        additionalProperties = Condition.class
    )
    private Map<String, Condition> conditions;
}
