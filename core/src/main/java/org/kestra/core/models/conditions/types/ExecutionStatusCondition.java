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
import org.kestra.core.models.flows.State;

import java.util.List;
import javax.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Introspected
public class ExecutionStatusCondition extends Condition {
    @Valid
    public List<State.Type> in;

    @Valid
    public List<State.Type> notIn;

    @Override
    public boolean test(Flow flow, Execution execution) {
        boolean result = true;

        if (this.in != null && !this.in.contains(execution.getState().getCurrent())) {
            result = false;
        }

        if (this.notIn != null && this.notIn.contains(execution.getState().getCurrent())) {
            result = false;
        }

        return result;
    }
}
