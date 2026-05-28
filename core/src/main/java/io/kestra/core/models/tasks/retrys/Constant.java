package io.kestra.core.models.tasks.retrys;

import java.time.Duration;
import java.time.Instant;

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
@Schema(title = "Constant retry", description = "Retry with a fixed delay between attempts.")
public class Constant extends AbstractRetry {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "constant";

    @NotNull
    private Duration interval;

    @Override
    public <T> RetryPolicyBuilder<T> toPolicy() {
        RetryPolicyBuilder<T> policy = super.toPolicy();
        return policy.withDelay(interval);
    }

    @Override
    public Instant nextRetryDate(Integer attemptCount, Instant lastAttempt) {
        return lastAttempt.plus(interval);
    }

    @AssertTrue(message = "'interval' must be less than 'maxDuration'")
    @JsonIgnore
    boolean isIntervalLessThanMaxDuration() {
        if (getMaxDuration() == null || interval == null) return true;
        return getMaxDuration().compareTo(interval) > 0;
    }
}
