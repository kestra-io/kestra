package org.kestra.core.models.triggers.types;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.ConditionService;
import org.kestra.core.utils.IdUtils;

import java.util.*;
import javax.annotation.Nullable;
import javax.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Slf4j
public class Flow extends AbstractTrigger {
    @Nullable
    private Map<String, Object> inputs;

    @Valid
    private List<Condition> conditions;

    public Optional<Execution> evaluate(RunContext runContext, org.kestra.core.models.flows.Flow flow, Execution current) {
        List<Condition> conditions = this.conditions == null ? new ArrayList<>() : this.conditions;

        if (!ConditionService.valid(conditions, flow, current)) {
            return Optional.empty();
        }

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .variables(ImmutableMap.of(
                "trigger", ImmutableMap.of(
                    "executionId", current.getId()
                )
            ));

        try {
            builder.inputs(runContext.render(this.inputs == null ? new HashMap<>() : this.inputs));
            return Optional.of(builder.build());
        } catch (Exception e) {
            Execution newExecution = builder.build().failedExecutionFromExecutor(e).getExecution();

            log.warn(
                "Failed to trigger flow {}.{} for trigger {}, create a failed execution '{}'",
                newExecution.getNamespace(),
                newExecution.getFlowId(),
                this.getId(),
                newExecution.getId(),
                e
            );

            return Optional.of(newExecution);
        }
    }
}
