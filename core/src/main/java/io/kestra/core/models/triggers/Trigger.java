package io.kestra.core.models.triggers;

import io.kestra.core.models.HasUID;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.micronaut.core.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * DON'T USE THIS CLASS - ONLY REQUIRED FOR 2.0 MIGRATION
 */
@Deprecated(forRemoval = true, since="2.0.0")
@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
public class Trigger extends TriggerContext implements HasUID {
    @Nullable
    private String executionId;

    @Nullable
    private Instant updatedDate;

    @Nullable
    private ZonedDateTime evaluateRunningDate; // this is used as an evaluation lock to avoid duplicate evaluation

    @Nullable
    @Setter // it's unfortunate but neither toBuilder() not @With works so using @Setter here
    private String workerId;

    @Nullable
    private Set<String> executions;

    protected Trigger(TriggerBuilder<?, ?> b) {
        super(b);
        this.executionId = b.executionId;
        this.updatedDate = b.updatedDate;
        this.evaluateRunningDate = b.evaluateRunningDate;
    }

    public static TriggerBuilder<?, ?> builder() {
        return new TriggerBuilderImpl();
    }
    
    // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
    // See https://stackoverflow.com/questions/51947791/javadoc-cannot-find-symbol-error-when-using-lomboks-builder-annotation
    public static abstract class TriggerBuilder<C extends Trigger, B extends TriggerBuilder<C, B>> extends TriggerContextBuilder<C, B> {
    }

    /**
     * Converts this trigger to {@link TriggerState}.
     *
     * @param vNodes the number of virtual nodes.
     * @return the new {@link TriggerState}.
     */
    public TriggerState toTriggerState(int vNodes) {
        return TriggerState
            .builder()
            .tenantId(getTenantId())
            .namespace(getNamespace())
            .flowId(getFlowId())
            .triggerId(getTriggerId())
            .updatedAt(getUpdatedDate())
            .evaluatedAt(getDate().toInstant())
            .nextEvaluationDate(getNextExecutionDate().toInstant())
            .backfill(getBackfill())
            .stopAfter(getStopAfter())
            .disabled(getDisabled())
            .workerId(getWorkerId())
            .vnode(VNodes.computeVNodeFromTrigger(this, vNodes))
            .locked(getExecutionId() != null)
            .build();
    }
}
