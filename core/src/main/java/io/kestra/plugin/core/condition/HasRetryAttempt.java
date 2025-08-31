package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition that matches if any taskRun has retry attempts."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition when any flow task on retry enters the state specified in the `in` states under the HasRetryAttempt condition.",
            full = true,
            code = """
                id: flow_condition_hasretryattempt
                namespace: company.team
    
                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute when any flow task on retry enters a specific state(s)."
    
                triggers:
                  - id: flow_condition
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.HasRetryAttempt
                        in:
                          - FAILED
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.HasRetryAttemptCondition", "io.kestra.plugin.core.condition.HasRetryAttemptCondition"}
)
public class HasRetryAttempt extends Condition {
    @Valid
    @Schema(title = "List of states that are authorized.")
    private Property<List<State.Type>> in;

    @Valid
    @Schema(title = "List of states that aren't authorized.")
    private Property<List<State.Type>> notIn;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with null execution");
        }

        RunContext runContext = conditionContext.getRunContext();
        var stateInRendered = runContext.render(this.in).asList(String.class, conditionContext.getVariables());
        var stateNotInRendered = runContext.render(this.notIn).asList(String.class, conditionContext.getVariables());

        return conditionContext
            .getExecution()
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getAttempts().size() > 1)
            .flatMap(taskRun -> taskRun.getAttempts().stream())
            .anyMatch(taskRunAttempt -> {
                boolean result = true;

                if (!stateInRendered.contains(taskRunAttempt.getState().getCurrent())) {
                    result = false;
                }

                if (stateNotInRendered.contains(taskRunAttempt.getState().getCurrent())) {
                    result = false;
                }

                return result;
            });
    }
}
