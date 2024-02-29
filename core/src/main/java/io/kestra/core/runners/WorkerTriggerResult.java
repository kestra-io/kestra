package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Optional;
import jakarta.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class WorkerTriggerResult {
    Optional<Execution> execution;

    @NotNull
    TriggerContext triggerContext;

    // This is only needed to be able to check the interval for some obscure reasons in the AbstractScheduler,
    // check the 'FIXME' in it.
    @NotNull
    AbstractTrigger trigger;

    @Builder.Default
    Boolean success = true;
}
