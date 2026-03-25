package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.utils.IdUtils;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
    private WorkerTriggerData data;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return IdUtils.fromParts(
            data.tenantId(),
            data.namespace(),
            data.flowId(),
            trigger.getId()
        );
    }

    public static WorkerTriggerRunning of(WorkerTrigger workerTrigger, WorkerInstance workerInstance) {
        return WorkerTriggerRunning.builder()
            .trigger(workerTrigger.getTrigger())
            .data(workerTrigger.getData())
            .workerInstance(workerInstance)
            .build();
    }
}
