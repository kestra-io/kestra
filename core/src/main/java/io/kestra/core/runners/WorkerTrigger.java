package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTrigger extends WorkerJob {
    public static final String TYPE = "trigger";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    private AbstractTrigger trigger;

    @NotNull
    private TriggerContext triggerContext;

    @NotNull
    private WorkerTriggerData data;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return triggerContext.uid();
    }
}
