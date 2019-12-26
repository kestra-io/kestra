package org.kestra.core.models.tasks.retrys;

import lombok.Value;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.runners.WorkerTask;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Value
public class Random extends AbstractRetry {
    @NotNull
    private Duration minInterval;

    @NotNull
    private Duration maxInterval;

    @Override
    public RetryPolicy<WorkerTask> toPolicy() {
        return super.toPolicy()
            .withDelay(minInterval.toMillis(), maxInterval.toMillis(), ChronoUnit.MILLIS);
    }
}
