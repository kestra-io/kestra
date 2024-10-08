package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.utils.IdUtils;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * The Kestra event for killing a trigger.
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ExecutionKilledTrigger extends ExecutionKilled implements TenantInterface {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "trigger";

    String namespace;

    String flowId;

    String triggerId;

    public boolean isEqual(TriggerContext triggerContext) {
        return (triggerContext.getTenantId() == null || triggerContext.getTenantId().equals(this.tenantId)) &&
            triggerContext.getNamespace().equals(this.namespace) &&
            triggerContext.getFlowId().equals(this.flowId) &&
            triggerContext.getTriggerId().equals(this.triggerId);
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(
            this.getTenantId(),
            this.getNamespace(),
            this.getFlowId(),
            this.getTriggerId()
        );
    }
}
