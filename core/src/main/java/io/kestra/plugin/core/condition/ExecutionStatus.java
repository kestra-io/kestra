package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;

import java.util.List;
import jakarta.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Condition based on execution status.")
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow based on execution status of another flow(s).",
            full = true,
            code = """
                id: flow_condition_executionstatus
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute when any flow enters FAILED or KILLED state."
                
                triggers:
                  - id: flow_trigger
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - KILLED
            """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.ExecutionStatusCondition", "io.kestra.plugin.core.condition.ExecutionStatusCondition"}
)
public class ExecutionStatus extends Condition {
    @Valid
    @Schema(title = "List of states that are authorized.")
    @PluginProperty
    private List<State.Type> in;

    @Valid
    @Schema(title = "List of states that aren't authorized.")
    @PluginProperty
    private List<State.Type> notIn;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with null execution");
        }

        boolean result = true;

        if (this.in != null && !this.in.contains(conditionContext.getExecution().getState().getCurrent())) {
            result = false;
        }

        if (this.notIn != null && this.notIn.contains(conditionContext.getExecution().getState().getCurrent())) {
            result = false;
        }

        return result;
    }
}
