package io.kestra.core.models.triggers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Trigger extends TriggerContext {
    @Nullable
    private String executionId;

    @Nullable
    private State.Type executionCurrentState;

    @Nullable
    private Instant updatedDate;

    @Nullable
    private ZonedDateTime evaluateRunningDate; // this is used as an evaluation lock to avoid duplicate evaluation

    public String uid() {
        return uid(this);
    }

    public static String uid(Trigger trigger) {
        return IdUtils.fromParts(
            trigger.getTenantId(),
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        );
    }

    public static String uid(Execution execution) {
        return IdUtils.fromParts(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getTrigger().getId()
        );
    }

    public static String uid(Flow flow, AbstractTrigger abstractTrigger) {
        return IdUtils.fromParts(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            abstractTrigger.getId()
        );
    }

    /**
     * Create a new Trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(Flow flow, AbstractTrigger abstractTrigger) {
        return Trigger.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .triggerId(abstractTrigger.getId())
            .stopAfter(abstractTrigger.getStopAfter())
            .build();
    }

    /**
     * Create a new Trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext) {
        return Trigger.builder()
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .backfill(triggerContext.getBackfill())
            .stopAfter(triggerContext.getStopAfter())
            .disabled(triggerContext.getDisabled())
            .build();
    }

    /**
     * Create a new Trigger from polling trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, ZonedDateTime nextExecutionDate) {
        return Trigger.builder()
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .nextExecutionDate(nextExecutionDate)
            .backfill(triggerContext.getBackfill())
            .stopAfter(triggerContext.getStopAfter())
            .disabled(triggerContext.getDisabled())
            .build();
    }

    /**
     * Create a new Trigger with execution information.
     * <p>
     * This is used to lock the trigger while an execution is running, it will also erase the evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, Execution execution) {
        return Trigger.builder()
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .executionId(execution.getId())
            .updatedDate(Instant.now())
            .nextExecutionDate(triggerContext.getNextExecutionDate())
            .backfill(triggerContext.getBackfill())
            .stopAfter(triggerContext.getStopAfter())
            .disabled(triggerContext.getDisabled())
            .build();
    }

    /**
     * Create a new Trigger with execution information and specific nextExecutionDate.
     * This one is use when starting a schedule execution as the nextExecutionDate come from the execution variables
     * <p>
     * This is used to lock the trigger while an execution is running, it will also erase the evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, Execution execution, ZonedDateTime nextExecutionDate) {
        return Trigger.builder()
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .executionId(execution.getId())
            .updatedDate(Instant.now())
            .nextExecutionDate(nextExecutionDate)
            .backfill(triggerContext.getBackfill())
            .stopAfter(triggerContext.getStopAfter())
            .disabled(triggerContext.getDisabled())
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
            .flowRevision(execution.getFlowRevision())
            .triggerId(execution.getTrigger().getId())
            .date(trigger.getDate())
            .nextExecutionDate(trigger.getNextExecutionDate())
            .executionId(execution.getId())
            .executionCurrentState(execution.getState().getCurrent())
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
        return Trigger.builder()
            .tenantId(trigger.getTenantId())
            .namespace(trigger.getNamespace())
            .flowId(trigger.getFlowId())
            .flowRevision(trigger.getFlowRevision())
            .triggerId(trigger.getTriggerId())
            .date(trigger.getDate())
            .nextExecutionDate(trigger.getNextExecutionDate())
            .evaluateRunningDate(evaluateRunningDate)
            .updatedDate(Instant.now())
            .backfill(trigger.getBackfill())
            .stopAfter(trigger.getStopAfter())
            .disabled(trigger.getDisabled())
            .build();
    }

    // Used to update trigger in flowListeners
    public static Trigger of(Flow flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext, Optional<Trigger> lastTrigger) throws Exception {
        return Trigger.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .triggerId(abstractTrigger.getId())
            .date(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .nextExecutionDate(((PollingTriggerInterface) abstractTrigger).nextEvaluationDate(conditionContext, Optional.empty()))
            .stopAfter(abstractTrigger.getStopAfter())
            .disabled(lastTrigger.map(TriggerContext::getDisabled).orElse(Boolean.FALSE))
            .backfill(null)
            .build();
    }

    public static Trigger update(Trigger currentTrigger, Trigger newTrigger, ZonedDateTime nextExecutionDate) throws Exception {
        Trigger updated = currentTrigger;

        // If a backfill is created, we update the currentTrigger
        // and set the nextExecutionDate() as the previous one
        if (newTrigger.getBackfill() != null) {
            updated = currentTrigger.toBuilder()
                .backfill(
                    newTrigger
                        .getBackfill()
                        .toBuilder()
                        .end(newTrigger.getBackfill().getEnd() != null ? newTrigger.getBackfill().getEnd() : ZonedDateTime.now())
                        .currentDate(
                            newTrigger.getBackfill().getStart()
                        )
                        .previousNextExecutionDate(
                            currentTrigger.getNextExecutionDate())
                        .build())
                .build();
        } else {

        }

        return updated.toBuilder()
            .nextExecutionDate(newTrigger.getDisabled() ?
                null : nextExecutionDate)
            .disabled(newTrigger.getDisabled())
            .build();
    }

    public Trigger resetExecution(State.Type executionEndState) {
        // switch disabled automatically if the executionEndState is one of the stopAfter states
        Boolean disabled = this.getStopAfter() != null ? this.getStopAfter().contains(executionEndState) : this.getDisabled();

        return Trigger.builder()
            .tenantId(this.getTenantId())
            .namespace(this.getNamespace())
            .flowId(this.getFlowId())
            .flowRevision(this.getFlowRevision())
            .triggerId(this.getTriggerId())
            .date(this.getDate())
            .nextExecutionDate(this.getNextExecutionDate())
            .stopAfter(this.getStopAfter())
            .backfill(this.getBackfill())
            .disabled(disabled)
            .build();
    }

    public Trigger unlock() {
        return Trigger.builder()
            .tenantId(this.getTenantId())
            .namespace(this.getNamespace())
            .flowId(this.getFlowId())
            .flowRevision(this.getFlowRevision())
            .triggerId(this.getTriggerId())
            .date(this.getDate())
            .nextExecutionDate(this.getNextExecutionDate())
            .backfill(this.getBackfill())
            .stopAfter(this.getStopAfter())
            .disabled(this.getDisabled())
            .build();
    }

    // if the next date is after the backfill end, we remove the backfill
    // if not, we update the backfill with the next Date
    // which will be the base date to calculate the next one
    public Trigger checkBackfill() {
        if (this.getBackfill() != null) {
            Backfill backfill = this.getBackfill();
            if (this.getNextExecutionDate().isAfter(backfill.getEnd())) {

                return this.toBuilder().nextExecutionDate(backfill.getPreviousNextExecutionDate()).backfill(null).build();
            } else {

                return this.toBuilder()
                    .backfill(
                        backfill.toBuilder().currentDate(this.getNextExecutionDate()).build()
                    )
                    .build();
            }
        }
        return this;
    }
}
