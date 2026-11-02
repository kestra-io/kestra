package io.kestra.core.models.flows.sla.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@NoArgsConstructor
abstract class AbstractDurationSLA extends SLA implements ExecutionMonitoringSLA {

    @Override
    public Optional<Violation> evaluate(RunContext runContext, Execution execution, Map<String, Object> additionalParameters) throws InternalException {
        Duration executionDuration = Duration.between(execution.getState().getStartDate(), Instant.now());
        Duration slaDuration = (Duration) additionalParameters.get(SLA.SLA_DURATION_ATTRIBUTE);
        if (executionDuration.compareTo(slaDuration) > 0) {
            String reason = "execution duration of " + executionDuration.truncatedTo(ChronoUnit.MILLIS) + " exceed the " + durationKind() + " duration of " + slaDuration + ".";
            return Optional.of(new Violation(this.getId(), this.getBehavior(), this.getLabels(), reason));
        }

        return Optional.empty();
    }

    protected abstract String durationKind();
}
