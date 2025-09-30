package io.kestra.core.models.triggers;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Nullable;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

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
    private Integer vnode;
    
    @Nullable
    private Set<String> executions;
    
    @Nullable
    private Boolean locked;
    
    protected Trigger(TriggerBuilder<?, ?> b) {
        super(b);
        this.executionId = b.executionId;
        this.updatedDate = b.updatedDate;
        this.evaluateRunningDate = b.evaluateRunningDate;
        this.vnode = b.vnode;
    }

    public static TriggerBuilder<?, ?> builder() {
        return new TriggerBuilderImpl();
    }
    
    public static String uid(Execution execution) {
        return IdUtils.fromParts(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getTrigger().getId()
        );
    }

    public static String uid(FlowInterface flow, AbstractTrigger abstractTrigger) {
        return IdUtils.fromParts(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            abstractTrigger.getId()
        );
    }

    public String flowUid() {
        return FlowId.uidWithoutRevision(this.getTenantId(), this.getNamespace(), this.getFlowId());
    }

    /**
     * Create a new Trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(FlowInterface flow, AbstractTrigger abstractTrigger) {
        return Trigger.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(abstractTrigger.getId())
            .stopAfter(abstractTrigger.getStopAfter())
            .build();
    }

    /**
     * Create a new Trigger from polling trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, ZonedDateTime nextExecutionDate) {
        return fromContext(triggerContext)
            .nextExecutionDate(nextExecutionDate)
            .build();
    }

    /**
     * Create a new Trigger with execution information and specific nextExecutionDate.
     * This one is use when starting a schedule execution as the nextExecutionDate come from the execution variables
     * <p>
     * This is used to lock the trigger while an execution is running, it will also erase the evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, Execution execution, ZonedDateTime nextExecutionDate) {
        return fromContext(triggerContext)
            .executionId(execution.getId())
            .updatedDate(Instant.now())
            .nextExecutionDate(nextExecutionDate)
            .build();
    }

    /**
     * Create a new Trigger with execution information.
     * <p>
     * This is used to update the trigger with the execution information, it will also erase the trigger date.
     */
    public static Trigger of(Execution execution, Trigger trigger) {
        return Trigger.builder()
            .tenantId(execution.getTenantId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .triggerId(execution.getTrigger().getId())
            .date(trigger.getDate())
            .nextExecutionDate(trigger.getNextExecutionDate())
            .executionId(execution.getId())
            .updatedDate(Instant.now())
            .backfill(trigger.getBackfill())
            .stopAfter(trigger.getStopAfter())
            .disabled(trigger.getDisabled())
            .build();
    }

    /**
     * Create a new Trigger with an evaluate running date.
     * <p>
     * This is used to lock the trigger evaluation.
     */
    public static Trigger of(Trigger trigger, ZonedDateTime evaluateRunningDate) {
        return fromContext(trigger)
            .nextExecutionDate(trigger.getNextExecutionDate())
            .evaluateRunningDate(evaluateRunningDate)
            .updatedDate(Instant.now())
            .build();
    }

    // Used to update trigger in flowListeners
    public static Trigger of(FlowInterface flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext, Optional<Trigger> lastTrigger) throws Exception {
        ZonedDateTime nextDate = null;
        boolean disabled = lastTrigger.map(TriggerContext::getDisabled).orElse(Boolean.FALSE);

        if (abstractTrigger instanceof PollingTriggerInterface pollingTriggerInterface) {
            try {
                nextDate = pollingTriggerInterface.nextEvaluationDate(conditionContext, Optional.empty());
            } catch (InvalidTriggerConfigurationException e) {
                disabled = true;
            }
        }

        return Trigger.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(abstractTrigger.getId())
            .date(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .nextExecutionDate(nextDate)
            .stopAfter(abstractTrigger.getStopAfter())
            .disabled(disabled)
            .backfill(null)
            .build();
    }
    
    // Add this line and all is good

    private static TriggerBuilder<?, ?> fromContext(TriggerContext triggerContext) {
        return Trigger.builder()
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .backfill(triggerContext.getBackfill())
            .stopAfter(triggerContext.getStopAfter())
            .disabled(triggerContext.getDisabled());
    }

    // This is a hack to make JavaDoc working as annotation processor didn't run before JavaDoc.
    // See https://stackoverflow.com/questions/51947791/javadoc-cannot-find-symbol-error-when-using-lomboks-builder-annotation
    public static abstract class TriggerBuilder<C extends Trigger, B extends TriggerBuilder<C, B>> extends TriggerContextBuilder<C, B> {
    }
}
