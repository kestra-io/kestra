package org.kestra.core.models.triggers;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;

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
}
