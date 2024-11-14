package io.kestra.plugin.core.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.kestra.core.topologies.FlowTopologyService.SIMULATED_EXECUTION;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a flow if the flow filter preconditions are met in a time window.",
    description = """
        This example will trigger an execution of `myflow` once all flow filter conditions are met in a specific period of time (`timeSLA`) — by default, a `window` of 24 hours.

        To reproduce this example, you can create two upstream flows (`myflow1` and `myflow2`) as follows:

        ```yaml
        id: myflow1
        namespace: company.team
        tasks:
          - id: hello
            type: io.kestra.plugin.core.log.Log
            message: Hello from {{ flow.id }}
        ```

        ```yaml
        id: myflow2
        namespace: company.team
        tasks:
          - id: hello
            type: io.kestra.plugin.core.log.Log
            message: Hello from {{ flow.id }}
        ```
        Now, create `myflow` with the `Flow` trigger waiting for given `FlowFilters` to be met.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "A flow that is waiting for two other flows to run successfully within a 1-day-period.",
            code = """
                id: myflow
                namespace: company.team

                triggers:
                  - id: wait_for_upstream
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - id: poll_for_flows
                        type: io.kestra.plugin.core.condition.ExecutionsCondition
                        preconditions:
                          - namespace: company.team
                            flowId: myflow1
                            states: [SUCCESS, WARNING]
                          - namespace: company.team
                            flowId: myflow2
                            states: [SUCCESS]

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: I'm triggered after two flows!"""
        )
    }
)
public class ExecutionsCondition extends AbstractMultipleCondition {
    @NotNull
    @NotEmpty
    @Schema(title = "A list of preconditions to met, in the form of upstream flows.")
    @PluginProperty
    private List<UpstreamFlow> preconditions;

    /**
     * Will emulate multiple conditions based on the preconditions property.
     */
    @JsonIgnore
    @Override
    public Map<String, Condition> getConditions() {
        AtomicInteger conditionId = new AtomicInteger();
        return preconditions.stream()
            .map(upstreamFlow -> Map.entry(
                "condition_" + conditionId.incrementAndGet(),
                new UpstreamFlowCondition(upstreamFlow)
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Builder
    @Getter
    public static class UpstreamFlow {
        @NotNull
        @Schema(title = "The namespace of the flow.")
        @PluginProperty
        private String namespace;

        @Schema(title = "The flow id.")
        @PluginProperty
        private String flowId;

        @Schema(title = "The execution states.")
        @PluginProperty
        private List<State.Type> states;

        @Schema(title = "A key/value map of labels.")
        @PluginProperty
        private Map<String, Object> labels;
    }

    @Hidden
    public static class UpstreamFlowCondition extends Condition {
        private final UpstreamFlow upstreamFlow;

        private UpstreamFlowCondition(UpstreamFlow upstreamFlow) {
            this.upstreamFlow = Objects.requireNonNull(upstreamFlow);
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            if (upstreamFlow.namespace != null && !conditionContext.getExecution().getNamespace().equals(upstreamFlow.namespace)) {
                return false;
            }

            if (upstreamFlow.flowId != null && !conditionContext.getExecution().getFlowId().equals(upstreamFlow.flowId)) {
                return false;
            }

            // we need to only evaluate on namespace and flow for simulated executions
            if (ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION)) {
                return true;
            }

            if (upstreamFlow.states != null && !upstreamFlow.states.contains(conditionContext.getExecution().getState().getCurrent())) {
                return false;
            }

            if (upstreamFlow.labels != null) {
                boolean notMatched = upstreamFlow.labels.entrySet().stream()
                    .map(entry -> new Label(entry.getKey(), String.valueOf(entry.getValue())))
                    .anyMatch(label -> !conditionContext.getExecution().getLabels().contains(label));
                return !notMatched;
            }

            return true;
        }
    }
}
