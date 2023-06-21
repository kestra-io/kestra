package io.kestra.core.runners;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTrigger {
    @NotNull
    private AbstractTrigger trigger;

    @NotNull
    private TriggerContext triggerContext;

    @NotNull
    private ConditionContext conditionContext;
}
