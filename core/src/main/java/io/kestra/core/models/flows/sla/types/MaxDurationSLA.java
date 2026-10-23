package io.kestra.core.models.flows.sla.types;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;
import io.kestra.core.validations.DurationMax;

import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class MaxDurationSLA extends SLA implements ExecutionMonitoringSLA {
    @NotNull
    @DurationMax
    @DurationMin(millis = 1, message = "must be a positive duration")
    private Duration duration;

    @Override
    public Optional<Violation> evaluate(RunContext runContext, Execution execution, Map<String, Object> additionalParameters) throws InternalException {
        // FIXME duplicated with AvgDurationSLA
        Duration executionDuration = Duration.between(execution.getState().getStartDate(), Instant.now());
        Duration slaDuration = (Duration) additionalParameters.get(SLA.SLA_DURATION_ATTRIBUTE);
        if (executionDuration.compareTo(slaDuration) > 0) {
            String reason = "execution duration of " + executionDuration.truncatedTo(ChronoUnit.MILLIS) + " exceed the maximum duration of " + slaDuration + ".";
            return Optional.of(new Violation(this.getId(), this.getBehavior(), this.getLabels(), reason));
        }

        return Optional.empty();
    }

    @Override
    public Optional<Duration> computeDuration(RunContext runContext) {
        return Optional.of(duration);
    }
}
