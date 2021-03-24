package io.kestra.core.models.tasks.retrys;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import javax.validation.constraints.NotNull;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Constant extends AbstractRetry {
    @NotNull
    private Duration interval;

    @Override
    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = super.toPolicy();

        return policy
            .withDelay(interval);
    }
}
