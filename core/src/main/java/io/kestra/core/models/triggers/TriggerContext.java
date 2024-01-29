package io.kestra.core.models.triggers;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@SuperBuilder(toBuilder = true)
@ToString
@Getter
@NoArgsConstructor
@Introspected
public class TriggerContext implements TenantInterface {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]")
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
