package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.failsafe.RetryPolicyBuilder;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Exponential extends AbstractRetry {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "exponential";

    @NotNull
    private Duration interval;

    @NotNull
    private Duration maxInterval;

    private Double delayFactor;

    @Override
    public <T> RetryPolicyBuilder<T> toPolicy() {
        RetryPolicyBuilder<T> policy = super.toPolicy();

        if (this.delayFactor != null) {
            policy.withBackoff(this.interval.toMillis(), this.maxInterval.toMillis(), ChronoUnit.MILLIS, this.delayFactor);
        } else {
            policy.withBackoff(this.interval.toMillis(), this.maxInterval.toMillis(), ChronoUnit.MILLIS);
        }

        return policy;
    }

    @Override
    public Instant nextRetryDate(Integer attemptCount, Instant lastAttempt) {
        Duration computedInterval = interval.multipliedBy(
            (long) (this.delayFactor == null ? 2 : this.delayFactor.intValue()) * (attemptCount - 1)
        );
        Instant next =  lastAttempt.plus(computedInterval);
        if (next.isAfter(lastAttempt.plus(maxInterval))) {

            return lastAttempt.plus(maxInterval);
        }

        return next;
    }
}
