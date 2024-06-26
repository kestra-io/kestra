package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@NoArgsConstructor
public class WorkerTriggerRunning extends WorkerJobRunning {
    public static final String TYPE = "trigger";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    private AbstractTrigger trigger;

    @NotNull
    private Trigger triggerContext;

    @NotNull
    private ConditionContext conditionContext;

    @Override
    public String uid() {
        return triggerContext.uid();
    }

    public static WorkerTriggerRunning of(WorkerTrigger workerTrigger, WorkerInstance workerInstance, int partition) {
        return WorkerTriggerRunning.builder()
            .trigger(workerTrigger.getTrigger())
            .triggerContext(workerTrigger.getTriggerContext())
            .conditionContext(workerTrigger.getConditionContext())
            .workerInstance(workerInstance)
            .partition(partition)
            .build();
    }
}
