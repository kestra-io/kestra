package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.failsafe.RetryPolicyBuilder;
import io.kestra.core.validations.RandomRetryValidation;
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
@RandomRetryValidation
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
}
