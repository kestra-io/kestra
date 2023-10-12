package io.kestra.core.models.triggers;

import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import javax.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@ToString
@Getter
@NoArgsConstructor
@Introspected
public class TriggerContext {
    private String tenantId;

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private Integer flowRevision;

    @NotNull
    private String triggerId;

    @NotNull
    private ZonedDateTime date;

    public String uid() {
        return uid(this);
    }

    public static String uid(TriggerContext trigger) {
        return IdUtils.fromParts(
            trigger.getTenantId(),
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        );
    }
}
