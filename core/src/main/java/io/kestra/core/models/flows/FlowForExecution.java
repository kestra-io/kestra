package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.utils.ListUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
@NoArgsConstructor
public class FlowForExecution extends AbstractFlow {
    @Valid
    @NotEmpty
    List<TaskForExecution> tasks;

    @Valid
    List<TaskForExecution> errors;

    @Valid
    @JsonProperty("finally")
    List<TaskForExecution> _finally;

    @Valid
    List<AbstractTriggerForExecution> triggers;

    public static FlowForExecution of(Flow flow) {
        return FlowForExecution.builder()
            .id(flow.getId())
            .tenantId((flow.getTenantId()))
            .namespace(flow.getNamespace())
            .revision(flow.getRevision())
            .inputs(flow.getInputs())
            .tasks(flow.getTasks().stream().map(TaskForExecution::of).toList())
            .errors(ListUtils.emptyOnNull(flow.getErrors()).stream().map(TaskForExecution::of).toList())
            ._finally(ListUtils.emptyOnNull(flow.getFinally()).stream().map(TaskForExecution::of).toList())
            .triggers(ListUtils.emptyOnNull(flow.getTriggers()).stream().map(AbstractTriggerForExecution::of).toList())
            .disabled(flow.isDisabled())
            .deleted(flow.isDeleted())
            .build();
    }
}
