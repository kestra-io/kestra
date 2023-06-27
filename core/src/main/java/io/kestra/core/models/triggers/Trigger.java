package io.kestra.core.models.triggers;

import io.kestra.core.models.flows.State;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import io.micronaut.core.annotation.Nullable;
import javax.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Trigger extends TriggerContext {
    @NotNull
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
        return String.join("_", Arrays.asList(
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        ));
    }

    public static String uid(Execution execution) {
        return String.join("_", Arrays.asList(
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getTrigger().getId()
        ));
    }

    public static String uid(Flow flow, AbstractTrigger abstractTrigger) {
        return String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId(),
            abstractTrigger.getId()
        ));
    }

    /**
     * Create a new Trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(Flow flow, AbstractTrigger abstractTrigger) {
        return Trigger.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .triggerId(abstractTrigger.getId())
            .build();
    }

    /**
     * Create a new Trigger with no execution information and no evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext) {
        return Trigger.builder()
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .build();
    }

    /**
     * Create a new Trigger with execution information.
     *
     * This is used to lock the trigger while an execution is running, it will also erase the evaluation lock.
     */
    public static Trigger of(TriggerContext triggerContext, Execution execution) {
        return Trigger.builder()
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .executionId(execution.getId())
            .updatedDate(Instant.now())
            .build();
    }

    /**
     * Create a new Trigger with execution information.
     *
     * This is used to update the trigger with the execution information, it will also erase the trigger date.
     */
    public static Trigger of(Execution execution, ZonedDateTime date) {
        return Trigger.builder()
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .flowRevision(execution.getFlowRevision())
            .triggerId(execution.getTrigger().getId())
            .date(date)
            .executionId(execution.getId())
            .executionCurrentState(execution.getState().getCurrent())
            .updatedDate(Instant.now())
            .build();
    }

    /**
     * Create a new Trigger with an evaluate running date.
     *
     * This is used to lock the trigger evaluation.
     */
    public static Trigger of(TriggerContext triggerContext, ZonedDateTime evaluateRunningDate) {
        return Trigger.builder()
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .date(triggerContext.getDate())
            .evaluateRunningDate(evaluateRunningDate)
            .updatedDate(Instant.now())
            .build();
    }

    public Trigger resetExecution() {
        return Trigger.builder()
            .namespace(this.getNamespace())
            .flowId(this.getFlowId())
            .flowRevision(this.getFlowRevision())
            .triggerId(this.getTriggerId())
            .date(this.getDate())
            .build();
    }
}
