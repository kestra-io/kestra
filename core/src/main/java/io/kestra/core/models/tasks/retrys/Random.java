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
    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = super.toPolicy();

        return policy
            .withDelay(minInterval.toMillis(), maxInterval.toMillis(), ChronoUnit.MILLIS);
    }
}
