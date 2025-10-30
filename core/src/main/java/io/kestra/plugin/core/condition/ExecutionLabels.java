package io.kestra.plugin.core.condition;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.validations.NoSystemLabelValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition that checks labels of an execution."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow based on execution of another flow(s) with certain execution labels.",
            full = true,
            code = """
                id: flow_condition_executionlabels
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute when flow with specified labels enters FAILED state."
                
                triggers:
                  - id: flow_trigger
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionLabels
                        labels:
                          owner: john.doe
                          env: prod
                    states:
                      - FAILED
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.ExecutionLabelsCondition", "io.kestra.plugin.core.condition.ExecutionLabelsCondition"}
)
public class ExecutionLabels extends Condition {

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @NotNull
    @Schema(
        description = "List of labels to match in the execution.",
        implementation = Object.class, oneOf = {List.class, Map.class}
    )
    @PluginProperty
    List<@NoSystemLabelValidation Label> labels;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        for (Label label : this.labels) {
            if (conditionContext.getExecution().getLabels() == null || !conditionContext.getExecution().getLabels().contains(label)) {
                return false;
            }
        }
        return true;
    }
}
