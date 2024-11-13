package io.kestra.core.models.flows.sla.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.sla.ExecutionChangedSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TruthUtils;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@SuperBuilder
@Getter
@NoArgsConstructor
public class ExecutionConditionSLA extends SLA implements ExecutionChangedSLA {
    @NotNull
    @NotEmpty
    private String condition;

    @Override
    public Optional<Violation> evaluate(RunContext runContext, Execution execution) throws InternalException {
        String result = runContext.render(this.getCondition());
        if (TruthUtils.isTruthy(result)) {
            String reason = "condition met: " + this.getCondition() + ".";
            return Optional.of(new Violation(this.getId(), this.getBehavior(), this.getLabels(), reason));
        }

        return Optional.empty();
    }
}
