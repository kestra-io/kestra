package org.kestra.core.models.tasks.retrys;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import javax.validation.constraints.NotNull;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Exponential extends AbstractRetry {
    @NotNull
    private Duration interval;

    @NotNull
    private Duration maxDuration;

    private Double delayFactor;

    @Override
    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = super.toPolicy();

        if (this.delayFactor != null) {
            policy.withBackoff(this.interval.toMillis(), this.maxDuration.toMillis(), ChronoUnit.MILLIS, this.delayFactor);
        } else {
            policy.withBackoff(this.interval.toMillis(), this.maxDuration.toMillis(), ChronoUnit.MILLIS);
        }

        return policy;
    }
}
