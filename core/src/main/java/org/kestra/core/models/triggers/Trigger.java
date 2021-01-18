package org.kestra.core.models.triggers;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import java.util.Arrays;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Trigger extends TriggerContext {
    @NotNull
    private String executionId;

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

    public static Trigger of(Flow flow, AbstractTrigger abstractTrigger) {
        return Trigger.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .triggerId(abstractTrigger.getId())
            .build();
    }

    public static Trigger of(TriggerContext triggerContext, Execution execution) {
        return Trigger.builder()
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(triggerContext.getFlowRevision())
            .triggerId(triggerContext.getTriggerId())
            .executionId(execution.getId())
            .date(triggerContext.getDate())
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
