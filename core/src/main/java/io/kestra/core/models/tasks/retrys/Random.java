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
@Schema(title = "Random retry", description = "Retry with a random delay within a configurable range between attempts.")
public class Random extends AbstractRetry {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "random";

    @NotNull
    private Duration minInterval;

    @NotNull
    private Duration maxInterval;

    @Override
    public <T> RetryPolicyBuilder<T> toPolicy() {
        RetryPolicyBuilder<T> policy = super.toPolicy();
        return policy.withDelay(minInterval.toMillis(), maxInterval.toMillis(), ChronoUnit.MILLIS);
    }

    @Override
    public Instant nextRetryDate(Integer attemptCount, Instant lastAttempt) {
        java.util.Random random = new java.util.Random();
        long randomMillis = random.nextLong(minInterval.toMillis(), maxInterval.toMillis());
        return lastAttempt.plusMillis(randomMillis);
    }

    @AssertTrue(message = "'minInterval' must be less than 'maxDuration'")
    @JsonIgnore
    boolean isMinIntervalLessThanMaxDuration() {
        if (getMaxDuration() == null || minInterval == null) return true;
        return getMaxDuration().compareTo(minInterval) > 0;
    }

    @AssertTrue(message = "'maxInterval' must be less than 'maxDuration'")
    @JsonIgnore
    boolean isMaxIntervalLessThanMaxDuration() {
        if (getMaxDuration() == null || maxInterval == null) return true;
        return getMaxDuration().compareTo(maxInterval) > 0;
    }

    @AssertTrue(message = "'minInterval' must be less than 'maxInterval'")
    @JsonIgnore
    boolean isMinIntervalLessThanMaxInterval() {
        if (maxInterval == null || minInterval == null) return true;
        return maxInterval.compareTo(minInterval) > 0;
    }
}
