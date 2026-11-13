package io.kestra.core.tasks.test;

import java.util.List;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.execution.Fail;
import io.kestra.plugin.core.flow.Approval;

import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Plugin
public class ApprovalAfterTask extends Approval {
    @Override
    protected List<Task> beforeDecision() {
        return List.of(
            Fail.builder().id(this.getId() + "_check").type(Fail.class.getName()).condition(Property.ofExpression("{{ inputs.fail }}")).build(),
            Return.builder().id(this.getId() + "_prepare").type(Return.class.getName()).format(Property.ofExpression("{{ inputs.auto }}")).build()
        );
    }

    @Override
    protected boolean autoApproved(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(Property.<Boolean> ofExpression("{{ outputs." + this.getId() + "_prepare.value }}")).as(Boolean.class).orElse(false);
    }
}
