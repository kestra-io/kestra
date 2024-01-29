package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import jakarta.validation.constraints.NotNull;

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
    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = super.toPolicy();

        if (this.delayFactor != null) {
            policy.withBackoff(this.interval.toMillis(), this.maxInterval.toMillis(), ChronoUnit.MILLIS, this.delayFactor);
        } else {
            policy.withBackoff(this.interval.toMillis(), this.maxInterval.toMillis(), ChronoUnit.MILLIS);
        }

        return policy;
    }
}
