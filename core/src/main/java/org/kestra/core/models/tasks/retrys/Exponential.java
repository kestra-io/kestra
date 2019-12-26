package org.kestra.core.models.tasks.retrys;

import lombok.Value;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.runners.WorkerTask;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Value
public class Exponential extends AbstractRetry {
    @NotNull
    private Duration interval;

    @NotNull
    private Duration maxDuration;

    private Double delayFactor;

    @Override
    public RetryPolicy<WorkerTask> toPolicy() {
        RetryPolicy<WorkerTask> policy = super.toPolicy();

        if (this.delayFactor != null) {
            policy.withBackoff(this.interval.toMillis(), this.maxDuration.toMillis(), ChronoUnit.MILLIS, this.delayFactor);
        } else {
            policy.withBackoff(this.interval.toMillis(), this.maxDuration.toMillis(), ChronoUnit.MILLIS);
        }

        return policy;
    }
}
