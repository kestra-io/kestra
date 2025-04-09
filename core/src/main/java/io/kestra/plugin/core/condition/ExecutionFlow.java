package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
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

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a specific flow of an execution."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow based on execution of another flow.",
            full = true,
            code = """
                id: flow_condition_executionflow
                namespace: company.team
                
                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute when flow `flow_a` of namespace `company.team` enters RUNNING state."
                
                triggers:
                  - id: flow_trigger
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionFlow
                        flowId: flow_a
                        namespace: company.team
                    states:
                      - RUNNING
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.ExecutionFlowCondition", "io.kestra.plugin.core.condition.ExecutionFlowCondition"}
)
public class ExecutionFlow extends Condition {
    @NotNull
    @Schema(title = "The namespace of the flow.")
    private Property<String> namespace;

    @NotNull
    @Schema(title = "The flow id.")
    private Property<String> flowId;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with null execution");
        }

        RunContext runContext = conditionContext.getRunContext();
        return conditionContext.getExecution().getNamespace().equals(runContext.render(this.namespace).as(String.class, conditionContext.getVariables()).orElseThrow())
            && conditionContext.getExecution().getFlowId().equals(runContext.render(this.flowId).as(String.class, conditionContext.getVariables()).orElseThrow());
    }
}
