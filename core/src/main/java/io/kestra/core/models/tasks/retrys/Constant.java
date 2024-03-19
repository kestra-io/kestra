package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.Instant;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Constant extends AbstractRetry {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "constant";

    @NotNull
    private Duration interval;

    @Override
    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = super.toPolicy();

        return policy
            .withDelay(interval);
    }

    @Override
    public Instant getNextDate(Integer attemptCount, Instant lastAttempt) {
        return lastAttempt.plus(interval);
    }
}
