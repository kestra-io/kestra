package io.kestra.core.runners;

import java.util.Optional;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
@Deprecated(forRemoval = true, since = "2.0.0")
public class WorkerTriggerResult implements HasUID {
    Optional<Execution> execution;

    @NotNull
    TriggerContext triggerContext;

    // This is only needed to be able to check the interval for some obscure reasons in the AbstractScheduler,
    // check the 'FIXME' in it.
    @NotNull
    AbstractTrigger trigger;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return triggerContext.uid();
    }
}
