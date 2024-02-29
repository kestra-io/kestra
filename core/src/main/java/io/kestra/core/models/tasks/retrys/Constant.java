package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import jakarta.validation.constraints.NotNull;

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
}
