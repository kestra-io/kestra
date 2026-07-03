package io.kestra.core.models.flows;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.runners.ReusableInputsExpander;
import io.kestra.core.utils.ListUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
    List<TaskForExecution> afterExecution;

    @Valid
    List<AbstractTriggerForExecution> triggers;

    /**
     * Like {@link #of(Flow)} but inlines every {@code REUSABLE_INPUTS} reference (FORM grouping preserved) so the
     * execute form receives — and submits — the block's resolved inputs under the reference id. Use this on the
     * execute-form endpoints; {@link #of(Flow)} stays a pure projection for callers without an expander.
     */
    public static FlowForExecution of(Flow flow, ReusableInputsExpander reusableInputsExpander) {
        return of(flow).toBuilder()
            .inputs(flow.inlinedInputs(reusableInputsExpander))
            .build();
    }

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
            .afterExecution(ListUtils.emptyOnNull(flow.getAfterExecution()).stream().map(TaskForExecution::of).toList())
            .triggers(ListUtils.emptyOnNull(flow.getTriggers()).stream().map(AbstractTriggerForExecution::of).toList())
            .disabled(flow.isDisabled())
            .deleted(flow.isDeleted())
            .draft(flow.isDraft())
            .build();
    }

    @JsonIgnore
    @Override
    public String getSource() {
        return null;
    }

    @Override
    public FlowForExecution toDeleted() {
        throw new UnsupportedOperationException("Can't delete a FlowForExecution");
    }
}
