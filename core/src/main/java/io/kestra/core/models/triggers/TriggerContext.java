package io.kestra.core.models.triggers;

import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;

@SuperBuilder(toBuilder = true)
@ToString
@Getter
@NoArgsConstructor
@Introspected
public class TriggerContext {
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

    @Nullable
    private ZonedDateTime nextExecutionDate;

    @Nullable
    private Backfill backfill;

    @Builder.Default
    private Boolean disabled = Boolean.FALSE;

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
