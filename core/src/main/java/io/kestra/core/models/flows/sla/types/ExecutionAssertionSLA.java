package io.kestra.core.models.flows.sla.types;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ExecutionAssertionSLA extends SLA implements ExecutionChangedSLA {
    @NotNull
    @NotEmpty
    @JsonProperty("assert")
    private String _assert;

    @Override
    public Optional<Violation> evaluate(RunContext runContext, Execution execution) throws InternalException {
        String result = runContext.render(this.get_assert());
        if (!TruthUtils.isTruthy(result)) {
            String reason = "assertion is false: " + this.get_assert() + ".";
            return Optional.of(new Violation(this.getId(), this.getBehavior(), this.getLabels(), reason));
        }

        return Optional.empty();
    }
}
