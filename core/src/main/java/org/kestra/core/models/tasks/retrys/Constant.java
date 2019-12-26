package org.kestra.core.models.tasks.retrys;

import lombok.Value;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.runners.WorkerTask;

import javax.validation.constraints.NotNull;
import java.time.Duration;

@Value
public class Constant extends AbstractRetry {
    @NotNull
    private Duration interval;

    @Override
    public RetryPolicy<WorkerTask> toPolicy() {
        return super.toPolicy()
            .withDelay(interval);
    }
}
