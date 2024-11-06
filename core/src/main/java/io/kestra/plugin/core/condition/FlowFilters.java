package io.kestra.plugin.core.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
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
    title = "Run a flow if the flow filter conditions are met.",
    description = """
        This example will trigger an execution of `myflow` once all flow filter conditions are met in a specific period of time (`sla`) — by default, a `window` of 24 hours.

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
                        type: io.kestra.plugin.core.condition.FlowFilters
                        upstreamFlows:
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
public class FlowFilters extends AbstractMultipleCondition {
    @NotNull
    @NotEmpty
    @Schema(title = "The list of upstream flows to wait for.")
    @PluginProperty
    private List<UpstreamFlow> upstreamFlows;

    /**
     * Will emulate multiple conditions based on the upstreamFlows property.
     */
    @JsonIgnore
    @Override
    public Map<String, Condition> getConditions() {
        AtomicInteger conditionId = new AtomicInteger();
        return upstreamFlows.stream()
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
        private Property<String> namespace;

        @Schema(title = "The flow id.")
        private Property<String> flowId;

        @Schema(title = "The execution states.")
        private Property<List<State.Type>> states;
    }

    @Hidden
    public static class UpstreamFlowCondition extends Condition {
        private final UpstreamFlow upstreamFlow;

        private UpstreamFlowCondition(UpstreamFlow upstreamFlow) {
            this.upstreamFlow = Objects.requireNonNull(upstreamFlow);
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            String namespace = conditionContext.getRunContext().render(upstreamFlow.namespace).as(String.class).orElse(null);
            if (!conditionContext.getExecution().getNamespace().equals(namespace)) {
                return false;
            }

            if (upstreamFlow.flowId != null) {
                String flowId = conditionContext.getRunContext().render(upstreamFlow.flowId).as(String.class).orElse(null);
                if (!conditionContext.getExecution().getFlowId().equals(flowId)) {
                    return false;
                }
            }

            // we need to only evaluate on namespace and flow for simulated executions
            if (upstreamFlow.states != null && !ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION)) {
                List<State.Type> states = conditionContext.getRunContext().render(upstreamFlow.states).asList(State.Type.class);
                return states.contains(conditionContext.getExecution().getState().getCurrent());
            }

            return true;
        }
    }
}