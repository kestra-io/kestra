package io.kestra.core.models.triggers;

import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;

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

    @Nullable
    private List<State.Type> stopAfter;

    private Boolean disabled = Boolean.FALSE;

    protected TriggerContext(TriggerContextBuilder<?, ?> b) {
        this.tenantId = b.tenantId;
        this.namespace = b.namespace;
        this.flowId = b.flowId;
        this.flowRevision = b.flowRevision;
        this.triggerId = b.triggerId;
        this.date = b.date;
        this.nextExecutionDate = b.nextExecutionDate;
        this.backfill = b.backfill;
        this.stopAfter = b.stopAfter;
        this.disabled = b.disabled;
    }

    public static TriggerContextBuilder<?, ?> builder() {
        return new TriggerContextBuilderImpl();
    }

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

    public Boolean getDisabled() {
        return this.disabled != null ? this.disabled : Boolean.FALSE;
    }

    // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
    // See https://stackoverflow.com/questions/51947791/javadoc-cannot-find-symbol-error-when-using-lomboks-builder-annotation
    public static abstract class TriggerContextBuilder<C extends TriggerContext, B extends TriggerContextBuilder<C, B>> {
    }
}
