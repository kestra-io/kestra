package org.kestra.core.models.conditions.types;

import io.micronaut.core.annotation.Introspected;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Introspected
public class FlowCondition extends Condition {
    @NotNull
    public String namespace;

    @NotNull
    public String flowId;

    @Override
    public boolean test(Flow flow, Execution execution) {
        return execution.getNamespace().equals(this.namespace) && execution.getFlowId().equals(this.flowId);
    }
}
