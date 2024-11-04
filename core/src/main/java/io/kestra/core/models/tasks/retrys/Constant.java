package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.failsafe.RetryPolicyBuilder;
import io.kestra.core.validations.ConstantRetryValidation;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

@SuperBuilder
@Getter
@NoArgsConstructor
@ConstantRetryValidation
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
}
