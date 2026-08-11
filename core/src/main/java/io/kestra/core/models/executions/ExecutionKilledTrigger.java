package io.kestra.core.models.executions;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.triggers.TriggerId;
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

    public boolean isEqual(TriggerId triggerId) {
        return (triggerId.getTenantId() == null || Objects.equals(triggerId.getTenantId(), this.tenantId)) &&
            triggerId.getNamespace().equals(this.namespace) &&
            triggerId.getFlowId().equals(this.flowId) &&
            triggerId.getTriggerId().equals(this.triggerId);
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
