package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkerTrigger extends WorkerJob {
    public static final String TYPE = "trigger";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    /** The trigger plugin to execute. Also provides triggerId via {@code trigger.getId()}. */
    @NotNull
    private AbstractTrigger trigger;

    /** All data the worker needs to reconstruct TriggerContext, RunContext, and ConditionContext. */
    @NotNull
    private WorkerTriggerData data;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return triggerId().uid();
    }

    /** Builds a {@link TriggerId} from the wire data fields. */
    @JsonIgnore
    public TriggerId triggerId() {
        return TriggerId.of(data.tenantId(), data.namespace(), data.flowId(), trigger.getId());
    }
}
