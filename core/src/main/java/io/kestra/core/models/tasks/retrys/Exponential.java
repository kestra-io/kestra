package io.kestra.core.models.tasks.retrys;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.validations.ExponentialRetryValidation;

import dev.failsafe.RetryPolicyBuilder;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@ExponentialRetryValidation
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
        double factor = this.delayFactor == null ? 2d : this.delayFactor;
        double delayMillis = interval.toMillis() * Math.pow(factor, attemptCount - 1);
        long maxMillis = maxInterval.toMillis();
        // delayMillis may be Double.POSITIVE_INFINITY for very large attemptCount — the >= guard handles that correctly
        long cappedMillis = delayMillis >= maxMillis ? maxMillis : Math.round(delayMillis);
        return lastAttempt.plusMillis(cappedMillis);
    }
}
