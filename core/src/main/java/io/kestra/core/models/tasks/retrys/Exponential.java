package io.kestra.core.models.tasks.retrys;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.failsafe.RetryPolicyBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(title = "Exponential retry", description = "Retry with exponentially increasing delays between attempts.")
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
        Instant next = lastAttempt.plus(computedInterval);
        if (next.isAfter(lastAttempt.plus(maxInterval))) {

            return lastAttempt.plus(maxInterval);
        }

        return next;
    }

    @AssertTrue(message = "'interval' must be less than 'maxDuration'")
    @JsonIgnore
    boolean isIntervalLessThanMaxDuration() {
        if (getMaxDuration() == null || interval == null) return true;
        return getMaxDuration().compareTo(interval) > 0;
    }

    @AssertTrue(message = "'interval' must be less than 'maxInterval'")
    @JsonIgnore
    boolean isIntervalLessThanMaxInterval() {
        if (interval == null || maxInterval == null) return true;
        return interval.compareTo(maxInterval) < 0;
    }
}
